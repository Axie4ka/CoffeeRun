package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import ru.coffeerun.StubApi
import ru.coffeerun.data.network.OrderDto
import ru.coffeerun.data.network.OrderLineDto
import ru.coffeerun.data.network.OrderRequest
import ru.coffeerun.data.session.SessionStateStore
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

/** Сеть и подтверждённый кеш; порядок операций проверяется у OrdersCoordinator. */
class NetworkOrderRepositoryTest {
    private class FakeApi : StubApi() {
        val order = OrderDto(1037, "ACCEPTED", "MOSKOVSKAYA", "2026-10-31T09:15:00+03:00", null,
            listOf(OrderLineDto("latte", 2, 240)))
        var reads = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getOrders(): List<OrderDto> {
            reads++
            withContext(NonCancellable) { gate?.await() }
            if (fail) throw IOException("offline")
            return listOf(order)
        }
        override suspend fun getOrder(number: Int) = order
        override suspend fun placeOrder(request: OrderRequest) = order.copy(number = 1042, lines = request.lines)
    }
    private val sessions = SessionStateStore("token")
    private fun repo(api: FakeApi) = NetworkOrderRepository(api, DemoMenuRepository(), sessions)

    @Test fun constructor_doesNotLoad() = runTest {
        val api = FakeApi()
        val repository = repo(api)
        assertEquals(0, api.reads)
        assertFalse(repository.isLoaded.value)
        assertTrue(repository.orders.value.isEmpty())
    }

    @Test fun refresh_marksFullListLoaded() = runTest {
        val repository = repo(FakeApi())
        repository.refresh(sessions.currentSession())
        assertTrue(repository.isLoaded.value)
        assertEquals(listOf(1037), repository.orders.value.map { it.number })
    }

    @Test fun load_doesNotClaimFullHistoryLoaded() = runTest {
        val repository = repo(FakeApi())
        assertEquals(1037, repository.load(1037, sessions.currentSession())!!.number)
        assertFalse(repository.isLoaded.value)
        assertEquals(listOf(1037), repository.orders.value.map { it.number })
    }

    @Test fun place_doesNotClaimFullHistoryLoaded() = runTest {
        val repository = repo(FakeApi())
        val result = repository.place(listOf(CartLine(DemoMenu.CROISSANT, 180)), Shop.MOSKOVSKAYA,
            null, sessions.currentSession())
        assertTrue(result is PlaceOrderResult.Placed)
        assertFalse(repository.isLoaded.value)
        assertEquals(listOf(1042), repository.orders.value.map { it.number })
    }

    @Test fun failedRefresh_keepsPreviouslyLoadedData() = runTest {
        val api = FakeApi()
        val repository = repo(api)
        val session = sessions.currentSession()
        repository.refresh(session)
        api.fail = true
        assertTrue(runCatching { repository.refresh(session) }.exceptionOrNull() is IOException)
        assertTrue(repository.isLoaded.value)
        assertEquals(listOf(1037), repository.orders.value.map { it.number })
    }

    @Test fun clear_resetsDataAndLoadedFlag() = runTest {
        val repository = repo(FakeApi())
        repository.refresh(sessions.currentSession())
        sessions.replaceToken(null)
        repository.clear(sessions.currentSession())
        assertFalse(repository.isLoaded.value)
        assertTrue(repository.orders.value.isEmpty())
    }

    @Test fun lateRefresh_doesNotRestoreClearedData() = runTest {
        val api = FakeApi()
        val repository = repo(api)
        val old = sessions.currentSession()
        val reply = CompletableDeferred<Unit>().also { api.gate = it }
        val loading = async(start = CoroutineStart.UNDISPATCHED) { runCatching { repository.refresh(old) } }
        sessions.replaceToken(null)
        repository.clear(sessions.currentSession())
        reply.complete(Unit)
        assertTrue(loading.await().exceptionOrNull() is CancellationException)
        assertFalse(repository.isLoaded.value)
        assertTrue(repository.orders.value.isEmpty())
    }
}
