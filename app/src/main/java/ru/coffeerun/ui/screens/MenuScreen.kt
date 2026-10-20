package ru.coffeerun.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.ProductCard
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.snackbar

/**
 * Меню: логотип с адресом кофейни, вкладки разделов и список позиций.
 * Нажатие на карточку — [onProductClick], кнопка «+» — [onAddProduct].
 */
@Composable
fun MenuScreen(
    uiState: MenuUiState,
    onRetry: () -> Unit,
    onProductClick: (Product) -> Unit,
    onAddProduct: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val notify = MaterialTheme.snackbar
    var category by rememberSaveable { mutableStateOf(ProductCategory.COFFEE) }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // Выбор кофейни пока не реализован
        MenuHeader(onShopClick = notify::notifyPlaceholder)
        // Разделы одного уровня — вкладки, а не фильтр-чипы
        PrimaryTabRow(selectedTabIndex = category.ordinal) {
            ProductCategory.entries.forEach { entry ->
                Tab(
                    selected = entry == category,
                    onClick = { category = entry },
                    text = { Text(stringResource(entry.labelRes)) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (uiState) {
            MenuUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            MenuUiState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterVertically)
            ) {
                Text(stringResource(R.string.menu_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            is MenuUiState.Success -> LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.large,
                    vertical = MaterialTheme.spacing.medium
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                items(uiState.products.filter { it.category == category }, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onClick = { onProductClick(product) },
                        onAdd = { onAddProduct(product) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(onShopClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
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
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun MenuScreenPreview() {
    CoffeeRunTheme {
        ProvidePreviewNotifier { MenuScreen(
                uiState = MenuUiState.Success(LocalMenu.products),
                onRetry = {},
                onProductClick = {},
                onAddProduct = {}
            ) }
    }
}
