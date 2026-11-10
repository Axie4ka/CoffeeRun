package ru.coffeerun.ui.orders

import androidx.annotation.StringRes
import java.io.IOException
import ru.coffeerun.R
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order

/** Результат нужен графу навигации: переходить в корзину только после добавления. */
data class OrderRepeatResult(val added: Boolean, @StringRes val message: Int? = null)

/**
 * Адаптация истории к корзине: неизвестные каталогу товары не создаются заново.
 * Пока сохраняем прежнюю цену и опции; их актуализация — отдельный шаг с RepeatOrderUseCase.
 */
internal suspend fun repeatOrder(order: Order, menu: MenuRepository, cart: CartRepository): OrderRepeatResult {
    val products = try {
        menu.getMenu().associateBy { it.id }
    } catch (e: IOException) {
        return OrderRepeatResult(added = false, message = R.string.order_repeat_error)
    }
    val available = order.lines.mapNotNull { item ->
        products[item.productId]?.let { product ->
            CartLine(product, item.unitPrice, item.quantity, item.options)
        }
    }
    if (available.isEmpty()) return OrderRepeatResult(added = false, message = R.string.order_repeat_unavailable)
    available.forEach(cart::add)
    return OrderRepeatResult(
        added = true,
        message = R.string.order_repeat_partial.takeIf { available.size < order.lines.size }
    )
}
