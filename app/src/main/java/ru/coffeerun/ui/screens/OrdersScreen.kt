package ru.coffeerun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.formatDateTime
import ru.coffeerun.ui.util.formatTime
import ru.coffeerun.ui.util.summaryText
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.snackbar

/**
 * Заказы: активные сверху, выданные — в истории.
 * [onRepeat] кладёт позиции прошлого заказа обратно в корзину.
 */
@Composable
fun OrdersScreen(
    orders: List<Order>,
    onRepeat: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val notify = MaterialTheme.snackbar
    val (active, history) = orders.partition { it.isActive }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.orders_title))
        if (orders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.orders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                        // Экрана с деталями заказа пока нет
                        OrderCard(order, stringResource(R.string.order_details), notify::notifyPlaceholder)
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
private fun OrderCard(order: Order, actionLabel: String, onAction: () -> Unit) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.order_total, order.total),
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Выданный заказ отмечается нейтральным цветом, остальные статусы — акцентным. */
@Composable
private fun StatusBadge(status: OrderStatus) {
    val done = status == OrderStatus.PICKED_UP
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
                    Order(1042, OrderStatus.PREPARING, listOf(CartLine(Product.CROISSANT, 180, id = 1)), Shop.MOSKOVSKAYA, now),
                    Order(1037, OrderStatus.PICKED_UP, listOf(CartLine(Product.LATTE, 240, 2, id = 2)), Shop.STROITELEY, now - 172_800_000L)
                ),
                onRepeat = {}
            )
        }
    }
}
