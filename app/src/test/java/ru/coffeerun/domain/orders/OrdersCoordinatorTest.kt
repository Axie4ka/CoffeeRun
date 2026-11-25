package ru.coffeerun.domain.orders

import ru.coffeerun.data.repository.NetworkOrderRepository

import ru.coffeerun.data.*
import ru.coffeerun.testPickupPolicy
import java.time.Instant
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.domain.checkout.CheckoutResult
import ru.coffeerun.domain.checkout.PlaceOrderUseCase

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import ru.coffeerun.data.session.SessionStateStore
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.network.dto.OrderDto
import ru.coffeerun.data.network.dto.OrderLineDto
import ru.coffeerun.data.network.dto.OrderRequest
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop

/** Заказы на сервере: история после входа, один заказ, оформление, отмена, 409 и «нет сети», очистка при выходе. */
@OptIn(ExperimentalCoroutinesApi::class)
class OrdersCoordinatorTest {
    /** Сервер с одним заказом 1037; [fail] — следующие запросы падают с [failCode] (0 — нет сети). */
    private class FakeApi : StubApi() {
        val server = mutableListOf(
            OrderDto(1037, "PICKED_UP", "MOSKOVSKAYA", "2026-10-31T09:15:00+03:00", null, listOf(OrderLineDto("latte", 2, 240)))
        )
        var fail = false
        var failCode = 0
        var nextNumber = 1042
        var getCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        var ignoreListCancellation = false
        var orderReply: (suspend (Int) -> OrderDto)? = null
        var placeReply: (suspend (OrderRequest) -> OrderDto)? = null
        var cancelReply: (suspend (Int) -> OrderDto)? = null

        private fun maybeFail() {
            if (!fail) return
            if (failCode == 0) throw IOException("нет сети")
            throw HttpException(Response.error<Unit>(failCode, "".toResponseBody()))
        }

        override suspend fun getOrders(): List<OrderDto> {
            getCalls++
            val snapshot = server.toList()
            val waiting = gate
            if (ignoreListCancellation) withContext(NonCancellable) { waiting?.await() } else waiting?.await()
            maybeFail()
            return snapshot
        }

        override suspend fun placeOrder(attemptId: String, request: OrderRequest): OrderDto {
            placeReply?.let { return it(request) }
            maybeFail()
            val status = if (request.pickupAt != null) "ACCEPTED" else "PREPARING"
            return OrderDto(nextNumber++, status, request.shop, "2026-11-03T12:00:00+03:00", request.pickupAt, request.lines)
                .also { server.add(0, it) }
        }

        override suspend fun getOrder(number: Int): OrderDto {
            orderReply?.let { return it(number) }
            maybeFail()
            return server.find { it.number == number } ?: throw HttpException(Response.error<Unit>(404, "".toResponseBody()))
        }

        /** Как мок: отменить можно только ACCEPTED, иначе 409. */
        override suspend fun cancelOrder(number: Int): OrderDto {
            cancelReply?.let { return it(number) }
            maybeFail()
            val i = server.indexOfFirst { it.number == number }
            if (server[i].status != "ACCEPTED") throw HttpException(Response.error<Unit>(409, "".toResponseBody()))
            return server[i].copy(status = "CANCELLED").also { server[i] = it }
        }
    }

    private val sessions = SessionStateStore("demo-token")
    private val croissant = listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2))
    private val evening = 1_793_811_600_000L // 2026-11-04 20:00 МСК: заказ ко времени — ACCEPTED

    private suspend fun OrdersCoordinator.place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long? = null) =
        place(lines, shop, pickupAtMillis, sessions.currentSession())

    private suspend fun TestScope.repo(api: FakeApi, load: Boolean = true) =
        OrdersCoordinator(NetworkOrderRepository(api, DemoMenuRepository(), sessions), sessions,
            backgroundScope + UnconfinedTestDispatcher(testScheduler),
            testPickupPolicy { Instant.parse("2026-11-04T09:00:00Z").toEpochMilli() }).also {
            if (load && sessions.currentSession().isLoggedIn) it.refresh()
        }

    @Test
    fun login_requiresExplicitLoad_logout_clears() = runTest {
        sessions.replaceToken(null)
        val repo = repo(FakeApi())
        assertTrue(repo.orders.value.isEmpty())
        sessions.replaceToken("demo-token")
        assertTrue(repo.orders.value.isEmpty())
        repo.refresh()
        assertEquals(listOf(1037), repo.orders.value.map { it.number })
        assertEquals("latte", repo.find(1037)!!.lines.single().productId)
        assertEquals("Латте", repo.find(1037)!!.lines.single().name)
        sessions.replaceToken(null)
        assertTrue("чужие заказы на устройстве не остаются", repo.orders.value.isEmpty())
    }

    @Test
    fun place_addsOrderOnTop() = runTest {
        val repo = repo(FakeApi())
        val result = repo.place(croissant, Shop.STROITELEY)
        val order = (result as PlaceOrderResult.Placed).order
        assertEquals(1042, order.number)
        assertEquals(Shop.STROITELEY, order.shop)
        assertEquals(360, order.total)
        assertEquals(listOf(1042, 1037), repo.orders.value.map { it.number })
        assertEquals("«Заказ принят» находит заказ по номеру", order, repo.find(1042))
    }

    @Test
    fun place_conflict_isUnavailable_transportOrServerFailure_hasUnknownOutcome() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.fail = true
        api.failCode = 409
        assertEquals(PlaceOrderResult.Unavailable, repo.place(croissant, Shop.MOSKOVSKAYA))
        api.failCode = 500
        assertEquals(PlaceOrderResult.OutcomeUnknown, repo.place(croissant, Shop.MOSKOVSKAYA))
        api.failCode = 0
        assertEquals(PlaceOrderResult.OutcomeUnknown, repo.place(croissant, Shop.MOSKOVSKAYA))
        assertEquals("непринятые заказы в список не попадают", listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun simultaneousExplicitRefreshes_shareOneRequest() = runTest {
        sessions.replaceToken(null)
        val api = FakeApi().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        sessions.replaceToken("demo-token")
        val fromProfile = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val fromTab = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() } // и тут же открыли вкладку
        api.gate!!.complete(Unit)
        fromTab.await()
        fromProfile.await()
        assertEquals("второй запрос не ушёл", 1, api.getCalls)
        assertEquals(listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun refreshDuringFailingLoad_failsToo() = runTest {
        sessions.replaceToken(null)
        val api = FakeApi().apply { gate = CompletableDeferred(); fail = true }
        val repo = repo(api)
        sessions.replaceToken("demo-token")
        val fromProfile = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        val fromTab = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        api.gate!!.complete(Unit)
        assertTrue("вкладка покажет «Повторить», а не пустой список", fromTab.await().exceptionOrNull() is IOException)
        assertTrue(fromProfile.await().exceptionOrNull() is IOException)
    }

    @Test
    fun refresh_offline_isIOException_keepsList() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.fail = true
        val error = runCatching { repo.refresh() }.exceptionOrNull()
        assertEquals(IOException::class, error!!::class)
        assertEquals("неудачная загрузка не стирает то, что было", listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun cancel_accepted_marksCancelled_keepsLines() = runTest {
        val repo = repo(FakeApi())
        repo.place(croissant, Shop.MOSKOVSKAYA, pickupAtMillis = evening)
        assertTrue(repo.find(1042)!!.canCancel)
        val order = (repo.cancel(1042) as CancelOrderResult.Cancelled).order
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertFalse("отменённый заказ уходит в историю", order.isActive)
        assertEquals("croissant", order.lines.single().productId)
        assertEquals("Круассан", order.lines.single().name)
        assertEquals(order, repo.find(1042))
        assertEquals(listOf(1042, 1037), repo.orders.value.map { it.number })
    }

    @Test
    fun cancel_conflict_isTooLate_andShowsServerStatus() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        repo.place(croissant, Shop.MOSKOVSKAYA, pickupAtMillis = evening)
        api.server[0] = api.server[0].copy(status = "PREPARING") // пока клиент думал, заказ начали готовить
        assertEquals(CancelOrderResult.TooLate, repo.cancel(1042))
        assertEquals(OrderStatus.PREPARING, repo.find(1042)!!.status)
    }

    @Test
    fun cancel_offline_isNetworkError_keepsStatus() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        repo.place(croissant, Shop.MOSKOVSKAYA, pickupAtMillis = evening)
        api.fail = true
        assertEquals(CancelOrderResult.NetworkError, repo.cancel(1042))
        assertEquals(OrderStatus.ACCEPTED, repo.find(1042)!!.status)
    }

    @Test
    fun load_freshStatus_updatesList() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        repo.place(croissant, Shop.MOSKOVSKAYA, pickupAtMillis = evening)
        api.server[0] = api.server[0].copy(status = "READY") // заказ приготовили, пока экран был открыт
        val order = repo.load(1042)!!
        assertEquals(OrderStatus.READY, order.status)
        assertEquals("croissant", order.lines.single().productId)
        assertEquals("Круассан", order.lines.single().name)
        assertEquals("список «Заказы» видит тот же статус", OrderStatus.READY, repo.find(1042)!!.status)
    }

    @Test
    fun load_unknown_isNull_offline_isIOException() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        assertNull(repo.load(9999))
        api.fail = true
        val error = runCatching { repo.load(1037) }.exceptionOrNull()
        assertEquals(IOException::class, error!!::class)
        assertEquals("ошибка не стирает заказ из списка", OrderStatus.PICKED_UP, repo.find(1037)!!.status)
    }

    @Test
    fun load_missingInCache_insertsInDateOrder_withoutDuplicates() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val template = api.server.single()
        api.server += template.copy(number = 1020, placedAt = "2026-10-01T12:00:00+03:00")
        api.server += template.copy(number = 1042, placedAt = "2026-11-03T12:00:00+03:00")
        val older = repo.load(1020)!!
        assertEquals(older, repo.find(1020))
        assertEquals(listOf(1037, 1020), repo.orders.value.map { it.number })
        val newer = repo.load(1042)!!
        assertEquals(newer, repo.find(1042))
        repo.load(1042)
        assertEquals(listOf(1042, 1037, 1020), repo.orders.value.map { it.number })
    }

    @Test
    fun load_notFound_removesStaleCachedOrder() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.server.clear()
        assertNull(repo.load(1037))
        assertNull(repo.find(1037))
        assertTrue(repo.orders.value.isEmpty())
    }

    @Test
    fun lateLoad_afterLogout_doesNotRestoreCachedOrder() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        val old = api.server.single()
        api.orderReply = { withContext(NonCancellable) { reply.await() }; old }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.load(1037) } }
        sessions.replaceToken(null)
        assertTrue(repo.orders.value.isEmpty())
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertTrue(repo.orders.value.isEmpty())
    }

    @Test
    fun lateRefresh_afterLogout_doesNotRestoreHistory() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.gate = CompletableDeferred()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        sessions.replaceToken(null)
        api.gate!!.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertTrue(repo.orders.value.isEmpty())
    }

    @Test
    fun confirmedCheckoutUpdatesHistoryWithoutReplacingNewerStatus() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val session = sessions.currentSession()
        val existing = repo.find(1037)!!
        val confirmedAt = existing.confirmedAtMillis!!
        repo.publishConfirmed(existing.copy(status = OrderStatus.PREPARING, confirmedAtMillis = confirmedAt - 1), session)
        assertEquals(OrderStatus.PICKED_UP, repo.find(1037)?.status)
        repo.publishConfirmed(existing.copy(number = 1042, confirmedAtMillis = confirmedAt + 1), session)
        assertEquals(1042, repo.find(1042)?.number)
    }

    @Test
    fun queuedCheckoutChecksCartAgainBeforeSending() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        api.orderReply = {
            entered.complete(Unit)
            release.await()
            api.server.single()
        }
        val loading = async { repo.load(1037) }
        entered.await()
        var cartMatches = true
        val placing = async(start = CoroutineStart.UNDISPATCHED) {
            repo.place(croissant, Shop.MOSKOVSKAYA, session = sessions.currentSession()) { cartMatches }
        }
        cartMatches = false
        release.complete(Unit)
        loading.await()
        assertEquals(PlaceOrderResult.CartChanged, placing.await())
        assertEquals(1, api.server.size)
    }

    @Test
    fun latePlace_afterLogout_doesNotReturnSuccessOrRestoreHistory() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        api.placeReply = { request ->
            reply.await()
            OrderDto(1042, "ACCEPTED", request.shop, "2026-11-03T12:00:00+03:00", request.pickupAt, request.lines)
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.place(croissant, Shop.MOSKOVSKAYA) } }
        sessions.replaceToken(null)
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertTrue(repo.orders.value.isEmpty())
    }

    @Test
    fun lateCancel_afterNewLogin_doesNotChangeNewSessionOrder() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        val old = api.server.single().copy(status = "CANCELLED")
        api.cancelReply = { withContext(NonCancellable) { reply.await() }; old }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.cancel(1037) } }
        sessions.replaceToken(null)
        api.server[0] = api.server[0].copy(status = "PREPARING")
        sessions.replaceToken("demo-token")
        repo.refresh()
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(OrderStatus.PREPARING, repo.find(1037)?.status)
    }

    @Test
    fun lateNotFound_afterNewLogin_doesNotRemoveNewSessionOrder() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        api.orderReply = {
            withContext(NonCancellable) { reply.await() }
            throw HttpException(Response.error<Unit>(404, "".toResponseBody()))
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.load(1037) } }
        sessions.replaceToken(null)
        sessions.replaceToken("demo-token")
        repo.refresh()
        reply.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(OrderStatus.PICKED_UP, repo.find(1037)?.status)
    }

    @Test
    fun newLogin_doesNotWaitForOldUncooperativeRefresh() = runTest {
        val reply = CompletableDeferred<Unit>()
        val api = FakeApi().apply { gate = reply; ignoreListCancellation = true }
        val old = api.server.single()
        val repo = repo(api, load = false)
        val oldRequest = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        sessions.replaceToken(null)
        api.gate = null
        api.server.clear()
        api.server += old.copy(number = 2001)
        sessions.replaceToken("new-token")
        repo.refresh()
        assertEquals(listOf(2001), repo.orders.value.map { it.number })
        reply.complete(Unit)
        assertTrue(oldRequest.await().exceptionOrNull() is CancellationException)
        runCurrent()
        assertEquals(listOf(2001), repo.orders.value.map { it.number })
    }

    @Test
    fun sameTokenLoginWithoutLogout_replacesSessionHistory() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.server[0] = api.server.single().copy(number = 2002)
        sessions.replaceToken("demo-token")
        assertTrue(repo.orders.value.isEmpty())
        repo.refresh()
        assertEquals(listOf(2002), repo.orders.value.map { it.number })
        assertEquals(2, api.getCalls)
    }

    @Test
    fun cancellingOneRefreshWaiter_doesNotCancelSharedRequest() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val first = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        first.cancel()
        reply.complete(Unit)
        second.await()
        assertEquals(2, api.getCalls) // Начальная загрузка и одна общая повторная.
        assertEquals(listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun refreshAndPlace_preserveNewOrder() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val place = async(start = CoroutineStart.UNDISPATCHED) { repo.place(croissant, Shop.MOSKOVSKAYA) }
        reply.complete(Unit)
        refresh.await()
        val placed = place.await() as PlaceOrderResult.Placed
        assertEquals(listOf(placed.order.number, 1037), repo.orders.value.map { it.number })
    }

    @Test
    fun refreshAndCancel_preserveCancelledStatus() = runTest {
        val api = FakeApi().apply { server[0] = server[0].copy(status = "ACCEPTED") }
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val cancel = async(start = CoroutineStart.UNDISPATCHED) { repo.cancel(1037) }
        reply.complete(Unit)
        refresh.await()
        assertTrue(cancel.await() is CancelOrderResult.Cancelled)
        assertEquals(OrderStatus.CANCELLED, repo.find(1037)?.status)
    }

    @Test
    fun loadAndCancel_preserveCancelledStatus() = runTest {
        val api = FakeApi().apply { server[0] = server[0].copy(status = "ACCEPTED") }
        val repo = repo(api)
        val old = api.server.single()
        val reply = CompletableDeferred<Unit>()
        api.orderReply = { reply.await(); old }
        val load = async(start = CoroutineStart.UNDISPATCHED) { repo.load(1037) }
        val cancel = async(start = CoroutineStart.UNDISPATCHED) { repo.cancel(1037) }
        reply.complete(Unit)
        load.await()
        assertTrue(cancel.await() is CancelOrderResult.Cancelled)
        assertEquals(OrderStatus.CANCELLED, repo.find(1037)?.status)
    }

    @Test
    fun cancelConflict_withQueuedRefresh_loadsCurrentStatusWithoutDeadlock() = runTest {
        val api = FakeApi().apply { server[0] = server[0].copy(status = "ACCEPTED") }
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        api.cancelReply = {
            reply.await()
            api.server[0] = api.server[0].copy(status = "PREPARING")
            throw HttpException(Response.error<Unit>(409, "".toResponseBody()))
        }
        val cancel = async(start = CoroutineStart.UNDISPATCHED) { repo.cancel(1037) }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        reply.complete(Unit)
        assertEquals(CancelOrderResult.TooLate, cancel.await())
        refresh.await()
        assertEquals(OrderStatus.PREPARING, repo.find(1037)?.status)
    }

    @Test
    fun cancelledQueuedCancel_doesNotReachServer() = runTest {
        val api = FakeApi().apply { server[0] = server[0].copy(status = "ACCEPTED") }
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() }
        val cancel = async(start = CoroutineStart.UNDISPATCHED) { repo.cancel(1037) }
        cancel.cancel()
        reply.complete(Unit)
        refresh.await()
        cancel.join()
        assertEquals("Отменённое ожидание не отменяет заказ на сервере", "ACCEPTED", api.server.single().status)
        assertEquals(OrderStatus.ACCEPTED, repo.load(1037)?.status)
    }

    @Test
    fun failedLoad_releasesQueuedPlace() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        val reply = CompletableDeferred<Unit>()
        api.orderReply = { reply.await(); throw IOException("нет сети") }
        val load = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.load(1037) } }
        val place = async(start = CoroutineStart.UNDISPATCHED) { repo.place(croissant, Shop.MOSKOVSKAYA) }
        reply.complete(Unit)
        assertTrue(load.await().exceptionOrNull() is IOException)
        assertTrue(place.await() is PlaceOrderResult.Placed)
        assertEquals(listOf(1042, 1037), repo.orders.value.map { it.number })
    }

    @Test
    fun guest_doesNotSendPersonalRequest() = runTest {
        sessions.replaceToken(null)
        val api = FakeApi()
        var called = false
        api.orderReply = { called = true; api.server.single() }
        val repo = repo(api)
        assertTrue(runCatching { repo.load(1037) }.exceptionOrNull() is CancellationException)
        assertFalse(called)
        assertEquals(0, api.getCalls)
    }
    @Test
    fun expectedOldSession_cannotPlaceForNewAccount() = runTest {
        val api = FakeApi()
        val coordinator = repo(api)
        val expected = sessions.currentSession()
        var sent = false
        api.placeReply = { sent = true; api.server.single() }
        sessions.replaceToken("new-token")
        val error = runCatching {
            coordinator.place(croissant, Shop.MOSKOVSKAYA, null, expected)
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertFalse(sent)
        assertTrue(coordinator.orders.value.isEmpty())
    }

    @Test
    fun pickupExpiresWhileQueued_doesNotPostOrConsumeCart() = runTest {
        for (pickup in listOf(null, Instant.parse("2026-11-10T19:00:00Z").toEpochMilli())) {
            var current = Instant.parse("2026-11-10T18:44:00Z").toEpochMilli()
            val policy = PickupPolicy(now = { current })
            val api = FakeApi()
            val coordinator = OrdersCoordinator(NetworkOrderRepository(api, DemoMenuRepository(), sessions),
                sessions, backgroundScope + UnconfinedTestDispatcher(testScheduler), policy)
            val gate = CompletableDeferred<Unit>().also { api.gate = it }
            val loading = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh() }
            val cart = InMemoryCartRepository().apply { add(croissant.single()) }
            val before = cart.lines.value
            val purchase = async(start = CoroutineStart.UNDISPATCHED) {
                PlaceOrderUseCase(cart, coordinator, sessions, policy, ru.coffeerun.data.DemoMenuRepository())(Shop.MOSKOVSKAYA, pickup)
            }
            current = Instant.parse("2026-11-10T18:46:00Z").toEpochMilli()
            gate.complete(Unit)
            loading.await()
            assertEquals(CheckoutResult.InvalidPickupTime, purchase.await())
            assertEquals(before, cart.lines.value)
            assertEquals(1042, api.nextNumber)
        }
    }

}
