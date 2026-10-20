package ru.coffeerun.ui.screens

import ru.coffeerun.data.LocalMenu
import androidx.compose.foundation.layout.Arrangement
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
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.ui.components.EmptyState
import ru.coffeerun.ui.components.ProductImage
import ru.coffeerun.ui.components.QuantityStepper
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.components.TotalRow
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.priceSmall
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.optionsText

/**
 * Корзина: позиции со счётчиками и закреплённая панель с итогом и переходом к оформлению.
 * В пустой корзине — кнопка [onBrowseMenu].
 */
@Composable
fun CartScreen(
    uiState: CartUiState,
    onChangeQuantity: (id: Int, delta: Int) -> Unit,
    onRemove: (id: Int) -> Unit,
    onCheckout: () -> Unit,
    onBrowseMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.cart_title))
        if (uiState.lines.isEmpty()) {
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
                items(uiState.lines, key = { it.id }) { line ->
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
                    TotalRow(
                        total = uiState.total,
                        label = pluralStringResource(R.plurals.cart_total_items, uiState.count, uiState.count)
                    )
                    Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cart_checkout))
                    }
                }
            }
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
                        canDecrease = line.quantity > MIN_QUANTITY,
                        canIncrease = line.quantity < MAX_QUANTITY
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun CartScreenPreview() {
    CoffeeRunTheme {
        CartScreen(
            uiState = CartUiState(
                lines = listOf(
                    CartLine(LocalMenu.CROISSANT, unitPrice = 180, id = 1),
                    CartLine(LocalMenu.LATTE, unitPrice = 240, quantity = 2, id = 2)
                ),
                count = 3,
                total = 660
            ),
            onChangeQuantity = { _, _ -> },
            onRemove = {},
            onCheckout = {},
            onBrowseMenu = {}
        )
    }
}
