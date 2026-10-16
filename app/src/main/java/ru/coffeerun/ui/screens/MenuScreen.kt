package ru.coffeerun.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.ProductCard
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.snackbar

/**
 * Меню: заголовок с адресом кофейни, чипы разделов и список позиций.
 * Нажатие на карточку — [onProductClick], кнопка «+» — [onAddProduct].
 */
@Composable
fun MenuScreen(
    onProductClick: (Product) -> Unit,
    onAddProduct: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val notify = MaterialTheme.snackbar
    var category by rememberSaveable { mutableStateOf(ProductCategory.COFFEE) }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        MenuHeader(
            // Выбор кофейни и поиск пока не реализованы
            onShopClick = notify::notifyPlaceholder,
            onSearchClick = notify::notifyPlaceholder
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
        ) {
            ProductCategory.entries.forEach { entry ->
                val selected = entry == category
                FilterChip(
                    selected = selected,
                    onClick = { category = entry },
                    label = { Text(stringResource(entry.labelRes)) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.small
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            items(Product.entries.filter { it.category == category }, key = { it.name }) { product ->
                ProductCard(
                    product = product,
                    onClick = { onProductClick(product) },
                    onAdd = { onAddProduct(product) }
                )
            }
        }
    }
}

@Composable
private fun MenuHeader(onShopClick: () -> Unit, onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.clickable(onClick = onShopClick),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = stringResource(R.string.menu_choose_shop),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = stringResource(Shop.MOSKOVSKAYA.addressRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.menu_search))
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun MenuScreenPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        ProvidePreviewNotifier { MenuScreen(onProductClick = {}, onAddProduct = {}) }
    }
}
