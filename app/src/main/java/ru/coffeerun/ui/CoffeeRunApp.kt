package ru.coffeerun.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.navigation.CartRoute
import ru.coffeerun.ui.navigation.CheckoutRoute
import ru.coffeerun.ui.navigation.MenuRoute
import ru.coffeerun.ui.navigation.OrdersRoute
import ru.coffeerun.ui.navigation.PlacedRoute
import ru.coffeerun.ui.navigation.ProductRoute
import ru.coffeerun.ui.navigation.ProfileRoute
import ru.coffeerun.ui.screens.CartScreen
import ru.coffeerun.ui.screens.CheckoutScreen
import ru.coffeerun.ui.screens.MenuScreen
import ru.coffeerun.ui.screens.OrderPlacedScreen
import ru.coffeerun.ui.screens.OrdersScreen
import ru.coffeerun.ui.screens.ProductScreen
import ru.coffeerun.ui.screens.ProfileScreen
import ru.coffeerun.ui.util.snackbar

/**
 * Корень приложения: NavHost внутри одного Scaffold. Нижняя панель видна только
 * на вкладках; Product, Checkout и Placed открываются поверх них.
 * Корзина и заказы живут в общей [AppViewModel], в маршрутах — только аргументы.
 */
@Composable
fun CoffeeRunApp(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    // destination.route — строка, а Tab.route — объект, поэтому сравниваем по классу маршрута
    val currentTab = Tab.entries.find { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    val notify: () -> Unit = MaterialTheme.snackbar::notifyPlaceholder

    Scaffold(
        // У Product и Checkout свой SnackbarHost с тем же state — иначе сообщение покажется дважды
        snackbarHost = { if (currentTab != null) SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentTab != null) AppNavigationBar(
                selected = currentTab,
                cartCount = viewModel.cartCount,
                onSelect = navController::openTab
            )
        }
    ) { innerPadding ->
        // Отступы внешнего Scaffold нужны только вкладкам: у остальных экранов свой Scaffold
        val tabModifier = Modifier.padding(innerPadding)
        NavHost(navController, startDestination = MenuRoute) {
            composable<MenuRoute> {
                MenuScreen(
                    onProductClick = { navController.navigate(ProductRoute(it)) },
                    onAddProduct = { viewModel.addToCart(CartLine(it, unitPrice = it.price)) },
                    modifier = tabModifier
                )
            }

            composable<CartRoute> {
                CartScreen(
                    lines = viewModel.cart,
                    onChangeQuantity = viewModel::changeQuantity,
                    onRemove = viewModel::remove,
                    onCheckout = { navController.navigate(CheckoutRoute) },
                    onBrowseMenu = { navController.openTab(Tab.MENU) },
                    modifier = tabModifier
                )
            }

            composable<OrdersRoute> {
                OrdersScreen(
                    orders = viewModel.orders,
                    onRepeat = {
                        viewModel.repeat(it)
                        navController.openTab(Tab.CART)
                    },
                    onBrowseMenu = { navController.openTab(Tab.MENU) },
                    modifier = tabModifier
                )
            }

            composable<ProfileRoute> { ProfileScreen(modifier = tabModifier) }

            composable<ProductRoute> { backStackEntry ->
                ProductScreen(
                    product = backStackEntry.toRoute<ProductRoute>().product,
                    cartCount = viewModel.cartCount,
                    onBack = { navController.popBackStack() },
                    onFavorite = notify,
                    onShare = notify,
                    onCart = { navController.jumpToTab(Tab.CART) },
                    onAddToCart = viewModel::addToCart,
                    snackbarHostState = snackbarHostState
                )
            }

            composable<CheckoutRoute> {
                CheckoutScreen(
                    lines = viewModel.cart,
                    shop = Shop.MOSKOVSKAYA,
                    onBack = { navController.popBackStack() },
                    onConfirm = {
                        if (viewModel.placeOrder(Shop.MOSKOVSKAYA) != null) {
                            // Cart и Checkout снимаем: «назад» с «Заказ принят» ведёт в меню
                            navController.navigate(PlacedRoute) { popUpTo(MenuRoute) }
                        }
                    },
                    snackbarHostState = snackbarHostState
                )
            }

            composable<PlacedRoute> {
                val toMenu: () -> Unit = { navController.popBackStack(MenuRoute, inclusive = false) }
                val order = viewModel.lastOrder
                if (order == null) {
                    // После смерти процесса стек восстановился, а ViewModel — нет
                    LaunchedEffect(Unit) { toMenu() }
                } else {
                    OrderPlacedScreen(
                        order = order,
                        onToOrders = { navController.jumpToTab(Tab.ORDERS) },
                        onToMenu = toMenu
                    )
                }
            }
        }
    }
}

/** Нижняя панель: переключение вкладок с сохранением их состояния. */
private fun NavController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * Переход на вкладку с экрана поверх вкладок: этот экран закрываем, а не сохраняем,
 * иначе вкладка «Меню» потом восстановит его через restoreState.
 */
private fun NavController.jumpToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(MenuRoute)
    launchSingleTop = true
}
