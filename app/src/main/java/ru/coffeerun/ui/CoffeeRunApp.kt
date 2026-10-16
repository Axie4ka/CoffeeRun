package ru.coffeerun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.screens.CartScreen
import ru.coffeerun.ui.screens.CheckoutScreen
import ru.coffeerun.ui.screens.DrinkScreen
import ru.coffeerun.ui.screens.MenuScreen
import ru.coffeerun.ui.screens.OrderPlacedScreen
import ru.coffeerun.ui.screens.OrdersScreen
import ru.coffeerun.ui.screens.ProfileScreen
import ru.coffeerun.ui.util.snackbar

/** Экраны поверх вкладок: у них нет нижней навигации. */
private enum class Overlay { DRINK, CHECKOUT, PLACED }

// Навигация построена на двух состояниях вместо Navigation Compose: экранов
// мало, а переходы линейные. Когда появятся аргументы и глубокие ссылки,
// стоит перейти на Navigation Compose.
@Composable
fun CoffeeRunApp(
    viewModel: AppViewModel,
    snackbarHostState: SnackbarHostState
) {
    var tab by rememberSaveable { mutableStateOf(Tab.MENU) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    val notify: () -> Unit = MaterialTheme.snackbar::notifyPlaceholder

    val lastOrder = viewModel.lastOrder
    when (overlay) {
        Overlay.DRINK -> {
            BackHandler { overlay = null }
            DrinkScreen(
                cartCount = viewModel.cartCount,
                onBack = { overlay = null },
                onFavorite = notify,
                onShare = notify,
                onCart = {
                    tab = Tab.CART
                    overlay = null
                },
                onAddToCart = viewModel::addToCart,
                snackbarHostState = snackbarHostState
            )
        }

        Overlay.CHECKOUT -> {
            BackHandler { overlay = null }
            CheckoutScreen(
                lines = viewModel.cart,
                shop = Shop.MOSKOVSKAYA,
                onBack = { overlay = null },
                onConfirm = {
                    if (viewModel.placeOrder(Shop.MOSKOVSKAYA) != null) overlay = Overlay.PLACED
                },
                snackbarHostState = snackbarHostState
            )
        }

        Overlay.PLACED -> {
            val toMenu = {
                tab = Tab.MENU
                overlay = null
            }
            BackHandler(onBack = toMenu)
            // Заказ выставляется до перехода сюда, поэтому null не ожидается
            if (lastOrder != null) {
                OrderPlacedScreen(
                    order = lastOrder,
                    onClose = toMenu,
                    onToOrders = {
                        tab = Tab.ORDERS
                        overlay = null
                    },
                    onToMenu = toMenu
                )
            }
        }

        null -> {
            // С других вкладок «назад» ведёт в меню, а не закрывает приложение
            BackHandler(enabled = tab != Tab.MENU) { tab = Tab.MENU }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    AppNavigationBar(
                        selected = tab,
                        cartCount = viewModel.cartCount,
                        onSelect = { tab = it }
                    )
                }
            ) { innerPadding ->
                val content = Modifier.padding(innerPadding)
                when (tab) {
                    Tab.MENU -> MenuScreen(
                        // Экран настройки пока есть только у капучино
                        onProductClick = { if (it == Product.CAPPUCCINO) overlay = Overlay.DRINK else notify() },
                        onAddProduct = { viewModel.addToCart(CartLine(it, unitPrice = it.price)) },
                        modifier = content
                    )

                    Tab.CART -> CartScreen(
                        lines = viewModel.cart,
                        onChangeQuantity = viewModel::changeQuantity,
                        onRemove = viewModel::remove,
                        onCheckout = { overlay = Overlay.CHECKOUT },
                        modifier = content
                    )

                    Tab.ORDERS -> OrdersScreen(
                        orders = viewModel.orders,
                        onRepeat = {
                            viewModel.repeat(it)
                            tab = Tab.CART
                        },
                        modifier = content
                    )

                    Tab.PROFILE -> ProfileScreen(modifier = content)
                }
            }
        }
    }
}
