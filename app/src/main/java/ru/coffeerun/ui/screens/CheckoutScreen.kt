package ru.coffeerun.ui.screens

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.RadioRow
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.components.TotalRow
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.formatTime
import ru.coffeerun.ui.util.summaryText

/**
 * Оформление заказа: кофейня, время получения, способ оплаты и состав заказа.
 * Онлайн-оплата пока не реализована, поэтому кофейня и оплата показаны как информация.
 * «Ко времени» открывает диалог с выбором времени; проверку времени делает ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    lines: List<CartLine>,
    shop: Shop,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    pickupAtMillis: Long? = null,
    earliestPickup: LocalTime? = LocalTime.NOON,
    onPickupAsap: () -> Unit = {},
    onPickupTimeChosen: (hour: Int, minute: Int) -> Boolean = { _, _ -> true },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    // Горизонтальные поля задаём каждому блоку, а не всей колонке: ListItem уже
    // имеет собственный отступ и иначе съехал бы относительно остального.
    val gutter = Modifier.padding(horizontal = MaterialTheme.spacing.large)
    val transparentItem = ListItemDefaults.colors(containerColor = Color.Transparent)
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.pickup_time_invalid)

    if (showTimePicker && earliestPickup != null) {
        val initial = pickupAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: earliestPickup
        val timeState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.pickup_time_title)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    if (!onPickupTimeChosen(timeState.hour, timeState.minute)) {
                        scope.launch { snackbarHostState.showSnackbar(invalidMessage) }
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

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
            // Информация, а не действие: выбрать другую кофейню пока нельзя
            ListItem(
                headlineContent = { Text(stringResource(shop.addressRes)) },
                supportingContent = { Text(stringResource(R.string.shop_status)) },
                leadingContent = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                },
                colors = transparentItem
            )
            HorizontalDivider(gutter)

            SectionTitle(stringResource(R.string.section_pickup), gutter)
            Column(gutter.selectableGroup()) {
                RadioRow(
                    label = stringResource(R.string.pickup_asap),
                    supporting = stringResource(R.string.pickup_asap_hint),
                    selected = pickupAtMillis == null,
                    onClick = onPickupAsap
                )
                RadioRow(
                    label = stringResource(R.string.pickup_scheduled),
                    supporting = when {
                        earliestPickup == null -> stringResource(R.string.pickup_scheduled_closed)
                        pickupAtMillis != null -> formatTime(pickupAtMillis)
                        else -> stringResource(R.string.pickup_scheduled_hint)
                    },
                    selected = pickupAtMillis != null,
                    onClick = { showTimePicker = true },
                    enabled = earliestPickup != null
                )
            }
            HorizontalDivider(gutter)

            SectionTitle(stringResource(R.string.section_payment), gutter)
            ListItem(
                headlineContent = { Text(stringResource(R.string.payment_on_pickup_title)) },
                supportingContent = { Text(stringResource(R.string.payment_on_pickup_hint)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_payments), contentDescription = null) },
                colors = transparentItem
            )
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
    CoffeeRunTheme {
        CheckoutScreen(
            lines = listOf(
                CartLine(LocalMenu.CROISSANT, unitPrice = 180, id = 1),
                CartLine(LocalMenu.LATTE, unitPrice = 240, quantity = 2, id = 2)
            ),
            shop = Shop.MOSKOVSKAYA,
            onBack = {},
            onConfirm = {}
        )
    }
}
