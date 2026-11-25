package ru.coffeerun.ui.navigation

import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.orders.OrderDetailsScreen
import ru.coffeerun.ui.orders.OrderDetailsViewModel
import ru.coffeerun.ui.orders.OrdersScreen
import ru.coffeerun.ui.orders.OrdersViewModel
import androidx.navigation.NavGraphBuilder
import kotlinx.coroutines.CoroutineScope

internal fun NavGraphBuilder.ordersGraph(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    isLoggedIn: Boolean?,
    tabModifier: Modifier,
    appScope: CoroutineScope
) {
    composable<OrdersRoute> {
        val vm: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)
        val screenScope = rememberCoroutineScope()
        val orders by vm.orders.collectAsStateWithLifecycle()
        val load by vm.load.collectAsStateWithLifecycle()
        val cancelling by vm.cancelling.collectAsStateWithLifecycle()
        val refreshing by vm.refreshing.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val resources = LocalResources.current
        // Каждый показ вкладки: переход с другой вкладки, возврат со входа или с «Заказ принят»
        LaunchedEffect(Unit) { vm.onShown() }
        LaunchedEffect(message) {
            message?.let {
                // Сначала показать, потом сбросить: сброс меняет ключ эффекта и отменил бы снэкбар
                snackbarHostState.showSnackbar(resources.getString(it))
                vm.onMessageShown()
            }
        }
        OrdersScreen(
            orders = orders,
            hasSnapshot = vm.hasSnapshot.collectAsStateWithLifecycle().value,
            load = load,
            onRetry = vm::retry,
            onRepeat = {
                val selected = it
                screenScope.launch {
                    val result = vm.repeat(selected) ?: return@launch
                    if (result.added) navController.openTab(Tab.CART)
                    result.message?.let { message ->
                        appScope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
                    }
                }
            },
            onBrowseMenu = { navController.openTab(Tab.MENU) },
            modifier = tabModifier,
            isLoggedIn = isLoggedIn,
            onLogin = { navController.navigate(LoginRoute()) },
            cancelling = cancelling,
            onCancel = vm::cancel,
            refreshing = refreshing,
            onRefresh = vm::refresh,
            onOrderClick = { navController.navigate(OrderDetailsRoute(it.number)) }
        )
    }

    composable<OrderDetailsRoute> {
        val vm: OrderDetailsViewModel = viewModel(factory = OrderDetailsViewModel.Factory)
        val screenScope = rememberCoroutineScope()
        val locale = LocalConfiguration.current.locales[0]
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(vm, locale, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.refreshWhileVisible()
            }
        }
        val order by vm.order.collectAsStateWithLifecycle()
        val load by vm.load.collectAsStateWithLifecycle()
        val cancelling by vm.cancelling.collectAsStateWithLifecycle()
        val notFound by vm.notFound.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val resources = LocalResources.current
        // Такого заказа на сервере нет — смотреть нечего
        LaunchedEffect(notFound) { if (notFound) navController.popBackStack<OrderDetailsRoute>(inclusive = true) }
        LaunchedEffect(message) {
            message?.let {
                snackbarHostState.showSnackbar(resources.getString(it))
                vm.onMessageShown()
            }
        }
        OrderDetailsScreen(
            order = order,
            load = load,
            cancelling = cancelling,
            onBack = { navController.popBackStack() },
            onRetry = vm::retry,
            onCancel = vm::cancel,
            onRepeat = {
                screenScope.launch {
                    val result = vm.repeat() ?: return@launch
                    if (result.added) navController.jumpToTab(Tab.CART)
                    result.message?.let { message ->
                        appScope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
                    }
                }
            },
            snackbarHostState = snackbarHostState
        )
    }
}
