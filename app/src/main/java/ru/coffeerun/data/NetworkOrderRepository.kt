package ru.coffeerun.data

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.coffeerun.data.network.CoffeeRunApi
import ru.coffeerun.data.network.orderRequest
import ru.coffeerun.data.network.toModel
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.Shop

private const val HTTP_CONFLICT = 409

/**
 * Заказы на сервере (/orders). Как и избранное, следит за [isLoggedIn]: после входа загружает
 * историю, после выхода очищает. Сервер присылает в строке только productId — позиции берутся из [menu].
 */
class NetworkOrderRepository(
    private val api: CoffeeRunApi,
    private val menu: MenuRepository,
    isLoggedIn: Flow<Boolean>,
    scope: CoroutineScope
) : OrderRepository {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    override val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    init {
        scope.launch {
            isLoggedIn.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) {
                    // Не загрузилось — экран «Заказы» сам попросит повторить через refresh()
                    try { refresh() } catch (e: IOException) { }
                } else {
                    _orders.value = emptyList()
                }
            }
        }
    }

    override suspend fun refresh() {
        val products = menu.getMenu().associateBy { it.id }
        _orders.value = network { api.getOrders() }.mapNotNull { it.toModel(products) }
    }

    override suspend fun place(lines: List<CartLine>, shop: Shop, pickupAtMillis: Long?): PlaceOrderResult {
        require(lines.isNotEmpty()) { "Пустой заказ" }
        val dto = try {
            network { api.placeOrder(orderRequest(lines, shop, pickupAtMillis)) }
        } catch (e: IOException) {
            return if ((e.cause as? HttpException)?.code() == HTTP_CONFLICT) PlaceOrderResult.Unavailable else PlaceOrderResult.NetworkError
        }
        // Позиции только что были в корзине — меню для разбора ответа не нужно
        val order = dto.toModel(lines.associate { it.product.id to it.product }) ?: return PlaceOrderResult.NetworkError
        _orders.update { listOf(order) + it }
        return PlaceOrderResult.Placed(order)
    }

    override fun find(number: Int): Order? = _orders.value.find { it.number == number }

    /** Ответ с ошибкой и ответ не по контракту — тоже «не получилось», как нет сети. */
    private suspend fun <T> network(request: suspend () -> T): T = try {
        request()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}", e)
    } catch (e: SerializationException) {
        throw IOException("Ответ сервера не по контракту", e)
    }
}
