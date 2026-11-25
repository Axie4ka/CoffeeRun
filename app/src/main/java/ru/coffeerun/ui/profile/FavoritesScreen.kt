package ru.coffeerun.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.ui.components.EmptyState
import ru.coffeerun.ui.components.ProductCard
import ru.coffeerun.ui.components.RetryMessage
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.spacing

/** Избранное: список карточек (без «+», он нужен только в меню) или пустое состояние. */
@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onProductClick: (String) -> Unit,
    onBrowseMenu: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveMissing: (String) -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.favorites_title), onBack = onBack)
        when (uiState) {
            FavoritesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            FavoritesUiState.CatalogError -> RetryMessage(stringResource(R.string.favorites_catalog_error), onRetry)
            FavoritesUiState.Error -> RetryMessage(stringResource(R.string.load_error), onRetry)
            is FavoritesUiState.Success -> {
                if (uiState.isCached) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.favorites_cached_refresh)) }
                }
                if (uiState.products.isEmpty() && uiState.missingIds.isEmpty()) {
                    EmptyState(stringResource(R.string.favorites_empty), onBrowseMenu, Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = MaterialTheme.spacing.large,
                            top = MaterialTheme.spacing.large,
                            end = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.large + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                    ) {
                        items(uiState.products, key = { it.id }) { product ->
                            ProductCard(product, onClick = { onProductClick(product.id) })
                        }
                        items(uiState.missingIds.sorted(), key = { it }) { id ->
                            Row(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.small),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.favorite_missing_product, id), Modifier.weight(1f))
                                TextButton(onClick = { onRemoveMissing(id) }) {
                                    Text(stringResource(R.string.cart_remove))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
