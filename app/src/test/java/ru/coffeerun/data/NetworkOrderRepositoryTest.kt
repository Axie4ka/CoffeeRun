package ru.coffeerun.data

import ru.coffeerun.StubApi
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.coffeerun.data.network.OrderDto
import ru.coffeerun.data.network.OrderLineDto
import ru.coffeerun.data.network.OrderRequest
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

/** Заказы на сервере: история после входа, оформление, 409 и «нет сети», очистка при выходе. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkOrderRepositoryTest {
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

        private fun maybeFail() {
            if (!fail) return
            if (failCode == 0) throw IOException("нет сети")
            throw HttpException(Response.error<Unit>(failCode, "".toResponseBody()))
        }

        override suspend fun getOrders(): List<OrderDto> {
            getCalls++
            gate?.await()
            maybeFail()
            return server.toList()
        }

        override suspend fun placeOrder(request: OrderRequest): OrderDto {
            maybeFail()
            val status = if (request.pickupAt != null) "ACCEPTED" else "PREPARING"
            return OrderDto(nextNumber++, status, request.shop, "2026-11-03T12:00:00+03:00", request.pickupAt, request.lines)
                .also { server.add(0, it) }
        }
    }

    private val loggedIn = MutableStateFlow(true)
    private val croissant = listOf(CartLine(DemoMenu.CROISSANT, unitPrice = 180, quantity = 2))

    private fun TestScope.repo(api: FakeApi) =
        NetworkOrderRepository(api, DemoMenuRepository(), loggedIn, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun login_loadsHistory_logout_clears() = runTest {
        loggedIn.value = false
        val repo = repo(FakeApi())
        assertTrue(repo.orders.value.isEmpty())
        loggedIn.value = true
        assertEquals(listOf(1037), repo.orders.value.map { it.number })
        assertEquals(DemoMenu.LATTE, repo.find(1037)!!.lines.single().product)
        loggedIn.value = false
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
    fun place_conflict_isUnavailable_offline_isNetworkError() = runTest {
        val api = FakeApi()
        val repo = repo(api)
        api.fail = true
        api.failCode = 409
        assertEquals(PlaceOrderResult.Unavailable, repo.place(croissant, Shop.MOSKOVSKAYA))
        api.failCode = 500
        assertEquals(PlaceOrderResult.NetworkError, repo.place(croissant, Shop.MOSKOVSKAYA))
        api.failCode = 0
        assertEquals(PlaceOrderResult.NetworkError, repo.place(croissant, Shop.MOSKOVSKAYA))
        assertEquals("непринятые заказы в список не попадают", listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun refreshDuringLoginLoad_sharesOneRequest() = runTest {
        loggedIn.value = false
        val api = FakeApi().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        loggedIn.value = true // вход: репозиторий начал грузить и ждёт сервер
        val fromTab = async(start = CoroutineStart.UNDISPATCHED) { repo.refresh() } // и тут же открыли вкладку
        api.gate!!.complete(Unit)
        fromTab.await()
        assertEquals("второй запрос не ушёл", 1, api.getCalls)
        assertEquals(listOf(1037), repo.orders.value.map { it.number })
    }

    @Test
    fun refreshDuringFailingLoad_failsToo() = runTest {
        loggedIn.value = false
        val api = FakeApi().apply { gate = CompletableDeferred(); fail = true }
        val repo = repo(api)
        loggedIn.value = true
        val fromTab = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repo.refresh() } }
        api.gate!!.complete(Unit)
        assertTrue("вкладка покажет «Повторить», а не пустой список", fromTab.await().exceptionOrNull() is IOException)
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
}
