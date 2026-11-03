package ru.coffeerun.ui.menu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.data.LocalMenu
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.RetryMessage
import ru.coffeerun.ui.components.ProductCard
import ru.coffeerun.ui.components.ShopPickerSheet
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
    shop: Shop,
    onShopSelected: (Shop) -> Unit,
    onRetry: () -> Unit,
    onProductClick: (Product) -> Unit,
    onAddProduct: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    var showShopPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        MenuHeader(shop = shop, onShopClick = { showShopPicker = true })
        when (uiState) {
            MenuUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            MenuUiState.Error -> RetryMessage(stringResource(R.string.menu_error), onRetry)
            is MenuUiState.Success -> if (uiState.products.isEmpty()) {
                RetryMessage(stringResource(R.string.menu_empty), onRetry)
            } else {
                MenuFeed(
                    products = uiState.products,
                    onProductClick = onProductClick,
                    onAddProduct = onAddProduct
                )
            }
        }
    }
    if (showShopPicker) ShopPickerSheet(shop, onShopSelected, onDismiss = { showShopPicker = false })
}

/** Вкладки разделов и список позиций; показывается, только когда меню загружено. */
@Composable
private fun MenuFeed(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onAddProduct: (Product) -> Unit
) {
    val sections = remember(products) {
        ProductCategory.entries.mapNotNull { category ->
            products.filter { it.category == category }
                .takeIf { it.isNotEmpty() }
                ?.let { category to it }
        }
    }
    val headerIndexes = remember(sections) {
        sections.runningFold(0) { index, (_, items) -> index + 1 + items.size }.dropLast(1)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedIndex by remember(headerIndexes) {
        derivedStateOf {
            // Последнюю секцию часто нельзя докрутить до верха — когда список упёрся в конец, выбираем её
            if (!listState.canScrollForward && listState.canScrollBackward) headerIndexes.lastIndex
            else headerIndexes.indexOfLast { it <= listState.firstVisibleItemIndex }.coerceAtLeast(0)
        }
    }

    Column {
        // Разделы одного уровня — вкладки, а не фильтр-чипы
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            sections.forEachIndexed { index, (category, _) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { scope.launch { listState.animateScrollToItem(headerIndexes[index]) } },
                    text = { Text(stringResource(category.labelRes)) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = MaterialTheme.spacing.large,
                vertical = MaterialTheme.spacing.medium
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            sections.forEach { (category, items) ->
                stickyHeader( key = "header-$category", contentType = "header") {
                    Text(
                        text = stringResource(category.labelRes),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = MaterialTheme.spacing.small)
                            .semantics { heading() }
                    )
                }
                items(items, key = { it.id }, contentType = { "product" }) { product ->
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
private fun MenuHeader(shop: Shop, onShopClick: () -> Unit) {
    val notify = MaterialTheme.snackbar

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
                    text = stringResource(shop.addressRes),
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
        IconButton(onClick = notify::notifyPlaceholder) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.menu_search))
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun MenuScreenPreview() {
    CoffeeRunTheme {
        ProvidePreviewNotifier { MenuScreen(
                uiState = MenuUiState.Success(LocalMenu.products, LocalMenu.prices),
                shop = Shop.MOSKOVSKAYA,
                onShopSelected = {},
                onRetry = {},
                onProductClick = {},
                onAddProduct = {}
            ) }
    }
}
