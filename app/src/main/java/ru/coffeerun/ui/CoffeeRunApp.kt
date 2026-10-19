package ru.coffeerun.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.coffeerun.model.CartLine
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.screens.MenuScreen

// Старая навигация (tab + overlay + BackHandler) удалена. Сюда встаёт NavHost —
// см. docs/superpowers/plans/2026-10-19-navigation-compose.md, этапы 2–4.
// Пока показывается только меню; тесты в androidTest/NavigationTest.kt красные,
// пока навигация не реализована.
@Composable
fun CoffeeRunApp(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AppNavigationBar(
                selected = Tab.MENU,
                cartCount = viewModel.cartCount,
                onSelect = { /* TODO: navController.openTab(it) */ }
            )
        }
    ) { innerPadding ->
        MenuScreen(
            onProductClick = { /* TODO: navController.navigate(ProductRoute(it)) */ },
            onAddProduct = { viewModel.addToCart(CartLine(it, unitPrice = it.price)) },
            modifier = Modifier.padding(innerPadding)
        )
    }
}
