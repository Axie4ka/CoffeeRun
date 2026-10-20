package ru.coffeerun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.ui.components.EmptyState
import ru.coffeerun.ui.components.ProductCard
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.theme.spacing

/** Избранное: список карточек (без «+», он нужен только в меню) или пустое состояние. */
@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onProductClick: (String) -> Unit,
    onBrowseMenu: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.favorites_title), onBack = onBack)
        when (uiState) {
            FavoritesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is FavoritesUiState.Success ->
                if (uiState.products.isEmpty()) {
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
                    }
                }
        }
    }
}
