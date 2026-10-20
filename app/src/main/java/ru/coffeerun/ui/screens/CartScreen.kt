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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Product
import ru.coffeerun.data.MAX_QUANTITY
import ru.coffeerun.ui.components.ProductImage
import ru.coffeerun.ui.components.QuantityStepper
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.priceLarge
import ru.coffeerun.ui.theme.priceSmall
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.optionsText

/**
 * Корзина: позиции со счётчиками и закреплённая панель с итогом и переходом к оформлению.
 * В пустой корзине — кнопка [onBrowseMenu].
 */
@Composable
fun CartScreen(
    lines: List<CartLine>,
    onChangeQuantity: (id: Int, delta: Int) -> Unit,
    onRemove: (id: Int) -> Unit,
    onCheckout: () -> Unit,
    onBrowseMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.cart_title))
        if (lines.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.cart_empty),
                onBrowseMenu = onBrowseMenu,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.small
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                items(lines, key = { it.id }) { line ->
                    CartItemCard(
                        line = line,
                        onDecrease = { onChangeQuantity(line.id, -1) },
                        onIncrease = { onChangeQuantity(line.id, 1) },
                        onRemove = { onRemove(line.id) }
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.cart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Итог показывается один раз — в закреплённой панели над кнопкой
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.large,
                        vertical = MaterialTheme.spacing.medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    val count = lines.sumOf { it.quantity }
                    TotalRow(
                        total = lines.sumOf { it.total },
                        label = pluralStringResource(R.plurals.cart_total_items, count, count)
                    )
                    Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cart_checkout))
                    }
                }
            }
        }
    }
}

/** Пустой список: подпись и кнопка «Перейти в меню». Общая для корзины и заказов. */
@Composable
fun EmptyState(text: String, onBrowseMenu: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onBrowseMenu) { Text(stringResource(R.string.browse_menu)) }
        }
    }
}

@Composable
private fun CartItemCard(
    line: CartLine,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            ProductImage(product = line.product, size = 64.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(line.product.nameRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cart_remove)
                        )
                    }
                }
                val options = line.optionsText()
                if (options.isNotEmpty()) {
                    Text(
                        text = options,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.price_rub, line.total),
                        style = MaterialTheme.typography.priceSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    // Удаление — отдельной кнопкой, поэтому ниже 1 счётчик не опускается
                    QuantityStepper(
                        quantity = line.quantity,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        canDecrease = line.quantity > 1,
                        canIncrease = line.quantity < MAX_QUANTITY
                    )
                }
            }
        }
    }
}

/** Строка «Итого» с крупной суммой — общая для корзины и оформления. */
@Composable
fun TotalRow(
    total: Int,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.total_label)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.price_rub, total),
            style = MaterialTheme.typography.priceLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun CartScreenPreview() {
    CoffeeRunTheme {
        CartScreen(
            lines = listOf(
                CartLine(Product.CROISSANT, unitPrice = 180, id = 1),
                CartLine(Product.LATTE, unitPrice = 240, quantity = 2, id = 2)
            ),
            onChangeQuantity = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseMenu = {}
        )
    }
}
