package ru.coffeerun.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.RetryMessage
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.priceSmall
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.optionsText

/**
 * Заказ целиком: статус, время, кофейня, позиции с опциями и итог.
 * Внизу — «Отменить» (после подтверждения), если заказ ещё можно отменить, или «Повторить» для прошлых заказов.
 * [order] null — показывать пока нечего: идёт загрузка ([load]) или она не удалась.
 */
@Composable
fun OrderDetailsScreen(
    order: Order?,
    load: OrdersLoad,
    cancelling: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    if (confirmCancel && order != null && order.canCancel) {
        CancelOrderDialog(order.number, onConfirm = { confirmCancel = false; onCancel() }, onDismiss = { confirmCancel = false })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ScreenTopBar(title = order?.let { stringResource(R.string.order_title, it.number) }.orEmpty(), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (order != null && (order.canCancel || !order.isActive)) {
                Box(Modifier.navigationBarsPadding().padding(MaterialTheme.spacing.large)) {
                    if (order.canCancel) {
                        OutlinedButton(onClick = { confirmCancel = true }, enabled = !cancelling, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.order_cancel_confirm))
                        }
                    } else {
                        Button(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.order_repeat))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            order != null -> OrderContent(order, Modifier.padding(innerPadding))
            load == OrdersLoad.FAILED -> RetryMessage(stringResource(R.string.load_error), onRetry, Modifier.padding(innerPadding))
            else -> Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun OrderContent(order: Order, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.timeText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatusBadge(order.status)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(order.shop.addressRes), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        order.lines.forEach { line -> LineRow(line) }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.total_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.price_rub, order.total),
                style = MaterialTheme.typography.priceSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun LineRow(line: CartLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Размер уже в строке опций ниже — здесь только название и количество
            Text(stringResource(R.string.line_summary, line.product.name, line.quantity), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = line.optionsText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(stringResource(R.string.price_rub, line.total), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun OrderDetailsScreenPreview() {
    CoffeeRunTheme {
        OrderDetailsScreen(
            order = Order(
                1045, OrderStatus.ACCEPTED,
                listOf(CartLine(DemoMenu.CAPPUCCINO, 220, id = 1), CartLine(DemoMenu.CROISSANT, 180, 2, id = 2)),
                Shop.MOSKOVSKAYA, placedAtMillis = 0L, pickupAtMillis = 3_600_000L
            ),
            load = OrdersLoad.DONE, cancelling = false, onBack = {}, onRetry = {}, onCancel = {}, onRepeat = {}
        )
    }
}
