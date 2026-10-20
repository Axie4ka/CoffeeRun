package ru.coffeerun.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.coffeerun.R
import ru.coffeerun.model.itemCount
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.navigation.CartRoute
import ru.coffeerun.ui.navigation.CheckoutRoute
import ru.coffeerun.ui.navigation.FavoritesRoute
import ru.coffeerun.ui.navigation.MenuRoute
import ru.coffeerun.ui.navigation.OrdersRoute
import ru.coffeerun.ui.navigation.PlacedRoute
import ru.coffeerun.ui.navigation.ProductRoute
import ru.coffeerun.ui.navigation.ProfileRoute
import ru.coffeerun.ui.screens.CartScreen
import ru.coffeerun.ui.screens.CartViewModel
import ru.coffeerun.ui.screens.CheckoutScreen
import ru.coffeerun.ui.screens.CheckoutViewModel
import ru.coffeerun.ui.screens.FavoritesScreen
import ru.coffeerun.ui.screens.FavoritesViewModel
import ru.coffeerun.ui.screens.MenuScreen
import ru.coffeerun.ui.screens.MenuViewModel
import ru.coffeerun.ui.screens.OrderPlacedScreen
import ru.coffeerun.ui.screens.OrderPlacedViewModel
import ru.coffeerun.ui.screens.OrdersScreen
import ru.coffeerun.ui.screens.OrdersViewModel
import ru.coffeerun.ui.screens.ProductScreen
import ru.coffeerun.ui.screens.ProductUiState
import ru.coffeerun.ui.screens.ProductViewModel
import ru.coffeerun.ui.screens.ProfileScreen
import ru.coffeerun.ui.screens.ProfileViewModel

/**
 * Корень приложения: NavHost внутри одного Scaffold. Нижняя панель видна только
 * на вкладках; Product, Checkout и Placed открываются поверх них.
 * У каждого экрана своя ViewModel (живёт, пока экран в стеке); данные — в общих
 * репозиториях из [ru.coffeerun.AppContainer]. Навигация остаётся здесь, во View.
 */
@Composable
fun CoffeeRunApp(snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    // destination.route — строка, а Tab.route — объект, поэтому сравниваем по классу маршрута
    val currentTab = Tab.entries.find { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    // Вне NavHost — ViewModel уровня Activity, только ради счётчика на нижней панели
    val cartUiState by viewModel<CartViewModel>(factory = CartViewModel.Factory).uiState.collectAsStateWithLifecycle()

    Scaffold(
        // У Product и Checkout свой SnackbarHost с тем же state — иначе сообщение покажется дважды
        snackbarHost = { if (currentTab != null) SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentTab != null) AppNavigationBar(
                selected = currentTab,
                cartCount = cartUiState.count,
                onSelect = navController::openTab
            )
        }
    ) { innerPadding ->
        // Отступы внешнего Scaffold нужны только вкладкам: у остальных экранов свой Scaffold
        val tabModifier = Modifier.padding(innerPadding)
        NavHost(navController, startDestination = MenuRoute) {
            composable<MenuRoute> {
                val vm: MenuViewModel = viewModel(factory = MenuViewModel.Factory)
                val menuState by vm.uiState.collectAsStateWithLifecycle()
                val shop by vm.shop.collectAsStateWithLifecycle()
                MenuScreen(
                    uiState = menuState,
                    shop = shop,
                    onShopSelected = vm::selectShop,
                    onRetry = vm::loadMenu,
                    onProductClick = { navController.navigate(ProductRoute(it.id)) },
                    onAddProduct = vm::quickAdd,
                    modifier = tabModifier
                )
            }

            composable<CartRoute> {
                val vm: CartViewModel = viewModel(factory = CartViewModel.Factory)
                val cartState by vm.uiState.collectAsStateWithLifecycle()
                CartScreen(
                    uiState = cartState,
                    onChangeQuantity = vm::changeQuantity,
                    onRemove = vm::remove,
                    onCheckout = { navController.navigate(CheckoutRoute) },
                    onBrowseMenu = { navController.openTab(Tab.MENU) },
                    modifier = tabModifier
                )
            }

            composable<OrdersRoute> {
                val vm: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)
                val orders by vm.orders.collectAsStateWithLifecycle()
                OrdersScreen(
                    orders = orders,
                    onRepeat = {
                        vm.repeat(it)
                        navController.openTab(Tab.CART)
                    },
                    onBrowseMenu = { navController.openTab(Tab.MENU) },
                    modifier = tabModifier
                )
            }

            composable<ProfileRoute> {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
                val shop by vm.shop.collectAsStateWithLifecycle()
                val favoritesCount by vm.favoritesCount.collectAsStateWithLifecycle()
                ProfileScreen(
                    shop = shop,
                    favoritesCount = favoritesCount,
                    onShopSelected = vm::selectShop,
                    onFavoritesClick = { navController.navigate(FavoritesRoute) },
                    modifier = tabModifier
                )
            }

            composable<FavoritesRoute> {
                val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
                val favoritesState by vm.uiState.collectAsStateWithLifecycle()
                FavoritesScreen(
                    uiState = favoritesState,
                    onProductClick = { navController.navigate(ProductRoute(it)) },
                    onBrowseMenu = { navController.jumpToTab(Tab.MENU) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ProductRoute> {
                val vm: ProductViewModel = viewModel(factory = ProductViewModel.Factory)
                val lines by vm.cartLines.collectAsStateWithLifecycle()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                when (val state = uiState) {
                    ProductUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    ProductUiState.NotFound -> LaunchedEffect(Unit) { navController.popBackStack<ProductRoute>(inclusive = true) }
                    is ProductUiState.Success -> {
                        val context = LocalContext.current
                        val product = state.product
                        val name = stringResource(product.nameRes)
                        val price = stringResource(
                            if (product.isPriceFrom) R.string.price_from else R.string.price_rub,
                            product.price
                        )
                        val shareText = stringResource(R.string.share_product, name, price)
                        ProductScreen(
                            product = state.product,
                            form = state.form,
                            unitPrice = state.unitPrice,
                            cartCount = lines.itemCount,
                            onSizeSelected = vm::selectSize,
                            onMilkSelected = vm::selectMilk,
                            onIcedChange = vm::setIced,
                            onExtraToggle = vm::toggleExtra,
                            onNoteChange = vm::setNote,
                            onQuantityChange = vm::changeQuantity,
                            onAddToCart = vm::addToCart,
                            onBack = { navController.popBackStack() },
                            isFavorite = state.isFavorite,
                            onFavorite = vm::toggleFavorite,
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                                context.startActivity(Intent.createChooser(send, null))
                            },
                            onCart = { navController.jumpToTab(Tab.CART) },
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }

            composable<CheckoutRoute> {
                val vm: CheckoutViewModel = viewModel(factory = CheckoutViewModel.Factory)
                val lines by vm.lines.collectAsStateWithLifecycle()
                val shop by vm.shop.collectAsStateWithLifecycle()
                val placedOrderNumber by vm.placedOrderNumber.collectAsStateWithLifecycle()
                LaunchedEffect(placedOrderNumber) {
                    placedOrderNumber?.let { number ->
                        // Cart и Checkout снимаем: «назад» с «Заказ принят» ведёт в меню
                        navController.navigate(PlacedRoute(number)) { popUpTo(MenuRoute) }
                        vm.onPlacedOrderShown()
                    }
                }
                CheckoutScreen(
                    lines = lines,
                    shop = shop,
                    onBack = { navController.popBackStack() },
                    onConfirm = vm::placeOrder,
                    snackbarHostState = snackbarHostState
                )
            }

            composable<PlacedRoute> {
                val vm: OrderPlacedViewModel = viewModel(factory = OrderPlacedViewModel.Factory)
                val toMenu: () -> Unit = { navController.popBackStack(MenuRoute, inclusive = false) }
                val order = vm.order
                if (order == null) {
                    // После смерти процесса стек восстановился, а заказы в памяти — нет
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
