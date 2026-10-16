package ru.coffeerun.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.coffeerun.R
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.RadioRow
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.snackbar
import ru.coffeerun.ui.util.summaryText

private enum class PickupTime { ASAP, SCHEDULED }

/**
 * Оформление заказа: кофейня, время получения, способ оплаты и состав заказа.
 * Выбор кофейни, времени «ко времени» и онлайн-оплата пока не реализованы:
 * кофейня одна, а время и оплата только отображаются.
 */
@Composable
fun CheckoutScreen(
    lines: List<CartLine>,
    shop: Shop,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    val notify = MaterialTheme.snackbar
    var pickup by rememberSaveable { mutableStateOf(PickupTime.ASAP) }
    // Горизонтальные поля задаём каждому блоку, а не всей колонке: ListItem уже
    // имеет собственный отступ и иначе съехал бы относительно остального.
    val gutter = Modifier.padding(horizontal = MaterialTheme.spacing.large)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ScreenTopBar(title = stringResource(R.string.checkout_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    TotalRow(total = lines.sumOf { it.total })
                    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.checkout_confirm))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            SectionTitle(stringResource(R.string.section_shop), gutter)
            ListItem(
                headlineContent = { Text(stringResource(shop.addressRes)) },
                supportingContent = { Text(stringResource(R.string.shop_status)) },
                leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = notify::notifyPlaceholder)
            )
            HorizontalDivider(gutter)

            SectionTitle(stringResource(R.string.section_pickup), gutter)
            Column(gutter.selectableGroup()) {
                RadioRow(
                    label = stringResource(R.string.pickup_asap),
                    supporting = stringResource(R.string.pickup_asap_hint),
                    selected = pickup == PickupTime.ASAP,
                    onClick = { pickup = PickupTime.ASAP }
                )
                RadioRow(
                    label = stringResource(R.string.pickup_scheduled),
                    supporting = stringResource(R.string.pickup_scheduled_hint),
                    selected = pickup == PickupTime.SCHEDULED,
                    onClick = { pickup = PickupTime.SCHEDULED }
                )
            }
            HorizontalDivider(gutter)

            SectionTitle(stringResource(R.string.section_payment), gutter)
            Column(gutter.selectableGroup()) {
                RadioRow(
                    label = stringResource(R.string.payment_on_pickup),
                    supporting = stringResource(R.string.payment_on_pickup_hint),
                    selected = true,
                    onClick = {}
                )
                RadioRow(
                    label = stringResource(R.string.payment_online),
                    supporting = stringResource(R.string.payment_online_hint),
                    selected = false,
                    onClick = {},
                    enabled = false
                )
            }
            HorizontalDivider(gutter)

            SectionTitle(stringResource(R.string.section_order), gutter)
            lines.forEach { line ->
                Row(gutter.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = line.summaryText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.price_rub, line.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun CheckoutScreenPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        ProvidePreviewNotifier {
            CheckoutScreen(
                lines = listOf(
                    CartLine(Product.CROISSANT, unitPrice = 180, id = 1),
                    CartLine(Product.LATTE, unitPrice = 240, quantity = 2, id = 2)
                ),
                shop = Shop.MOSKOVSKAYA,
                onBack = {},
                onConfirm = {}
            )
        }
    }
}
