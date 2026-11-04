package ru.coffeerun.ui.orders

import ru.coffeerun.data.DemoMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.ui.components.EmptyState
import ru.coffeerun.ui.components.RetryMessage
import androidx.compose.material3.CircularProgressIndicator
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.components.SignInPrompt
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.priceSmall
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.formatDateTime
import ru.coffeerun.ui.util.formatTime
import ru.coffeerun.ui.util.summaryText

/**
 * Заказы: активные сверху, выданные и отменённые — в истории.
 * [onRepeat] кладёт позиции прошлого заказа обратно в корзину; [onCancel] вызывается после подтверждения в диалоге.
 */
@Composable
fun OrdersScreen(
    orders: List<Order>,
    onRepeat: (Order) -> Unit,
    onBrowseMenu: () -> Unit,
    modifier: Modifier = Modifier,
    isLoggedIn: Boolean? = true,
    onLogin: () -> Unit = {},
    load: OrdersLoad = OrdersLoad.DONE,
    onRetry: () -> Unit = {},
    cancelling: Int? = null,
    onCancel: (Order) -> Unit = {}
) {
    // Заказы — данные аккаунта: гостю приглашение войти; пока токен не прочитан — пусто, без мигания
    when (isLoggedIn) {
        null -> return Box(modifier.fillMaxSize())
        false -> return SignInPrompt(
            screenTitle = stringResource(R.string.orders_title),
            title = stringResource(R.string.orders_guest_title),
            text = stringResource(R.string.orders_guest_text),
            onLogin = onLogin,
            modifier = modifier
        )
        true -> Unit
    }
    val (active, history) = orders.partition { it.isActive }
    // Номер, а не заказ: переживает поворот экрана; заказ ушёл из активных — диалог закрывается сам
    var confirmNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    active.find { it.number == confirmNumber }?.let { order ->
        AlertDialog(
            onDismissRequest = { confirmNumber = null },
            title = { Text(stringResource(R.string.order_cancel_title, order.number)) },
            text = { Text(stringResource(R.string.order_cancel_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNumber = null
                    onCancel(order)
                }) { Text(stringResource(R.string.order_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNumber = null }) { Text(stringResource(R.string.order_cancel_keep)) }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.orders_title))
        // Список уже есть — показываем его, даже если обновить не удалось
        if (orders.isEmpty() && load == OrdersLoad.LOADING) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (orders.isEmpty() && load == OrdersLoad.FAILED) {
            RetryMessage(stringResource(R.string.load_error), onRetry, Modifier.weight(1f))
        } else if (orders.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.orders_empty),
                onBrowseMenu = onBrowseMenu,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                if (active.isNotEmpty()) {
                    item(key = "active") { GroupTitle(stringResource(R.string.orders_active)) }
                    items(active, key = { it.number }) { order ->
                        // Отменить можно только заказ ко времени, который ещё не готовят
                        if (order.canCancel) {
                            OrderCard(order, stringResource(R.string.order_cancel), actionEnabled = cancelling != order.number) {
                                confirmNumber = order.number
                            }
                        } else {
                            OrderCard(order)
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    item(key = "history") { GroupTitle(stringResource(R.string.orders_history)) }
                    items(history, key = { it.number }) { order ->
                        OrderCard(order, stringResource(R.string.order_repeat)) { onRepeat(order) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OrderCard(
    order: Order,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null
) {
    val address = stringResource(order.shop.addressRes)
    val meta = if (order.isActive) {
        stringResource(R.string.order_meta_active, formatTime(order.readyAtMillis), address)
    } else {
        stringResource(R.string.order_meta_history, formatDateTime(order.placedAtMillis), address)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.order_title, order.number),
                    style = MaterialTheme.typography.titleMedium
                )
                StatusBadge(order.status)
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = order.lines.map { it.summaryText() }.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider()
            Row(
                // Высота как у TextButton: карточки без кнопки не становятся ниже
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.total_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.price_rub, order.total),
                    style = MaterialTheme.typography.priceSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Выданный и отменённый заказы отмечаются нейтральным цветом, остальные статусы — акцентным. */
@Composable
private fun StatusBadge(status: OrderStatus) {
    val done = status == OrderStatus.PICKED_UP || status == OrderStatus.CANCELLED
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (done) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (done) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = stringResource(status.labelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.extraSmall
            )
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun OrdersScreenPreview() {
    val now = System.currentTimeMillis()
    CoffeeRunTheme(dynamicColor = false) {
        ProvidePreviewNotifier {
            OrdersScreen(
                orders = listOf(
                    Order(1042, OrderStatus.PREPARING, listOf(CartLine(DemoMenu.CROISSANT, 180, id = 1)), Shop.MOSKOVSKAYA, now),
                    Order(1037, OrderStatus.PICKED_UP, listOf(CartLine(DemoMenu.LATTE, 240, 2, id = 2)), Shop.STROITELEY, now - 172_800_000L)
                ),
                onRepeat = {},
                onBrowseMenu = {}
            )
        }
    }
}
