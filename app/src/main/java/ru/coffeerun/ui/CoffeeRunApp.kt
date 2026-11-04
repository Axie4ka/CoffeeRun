package ru.coffeerun.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.auth.SessionViewModel
import ru.coffeerun.ui.cart.CartViewModel
import ru.coffeerun.ui.navigation.CoffeeRunNavHost
import ru.coffeerun.ui.navigation.openTab

/**
 * Корень приложения: [CoffeeRunNavHost] внутри одного Scaffold. Нижняя панель видна только
 * на вкладках; Product, Checkout и Placed открываются поверх них.
 * У каждого экрана своя ViewModel (живёт, пока экран в стеке); данные — в общих
 * репозиториях из [ru.coffeerun.AppContainer]. Граф экранов и переходы — в [CoffeeRunNavHost].
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
    // Вошёл ли пользователь: оформление, заказы и избранное — только после входа
    val isLoggedIn by viewModel<SessionViewModel>(factory = SessionViewModel.Factory).isLoggedIn.collectAsStateWithLifecycle()

    Scaffold(
        // Статус-бар учитывают сами вкладки (TopAppBar / statusBarsPadding), иначе отступ сверху двойной;
        // снизу отступ даёт NavigationBar, он сам учитывает системную панель
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // У Product и Checkout свой SnackbarHost с тем же state — иначе сообщение покажется дважды
        snackbarHost = { if (currentTab != null) SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentTab != null) AppNavigationBar(
                selected = currentTab,
                cartCount = cartUiState.count,
                // Уже открытую вкладку не переоткрываем: openTab восстановил бы её старое сохранённое
                // состояние вместо текущего (например, после «К моим заказам»)
                onSelect = { tab -> if (tab != currentTab) navController.openTab(tab) }
            )
        }
    ) { innerPadding ->
        // Отступы внешнего Scaffold нужны только вкладкам: у остальных экранов свой Scaffold
        val tabModifier = Modifier.padding(innerPadding)
        CoffeeRunNavHost(navController, isLoggedIn, snackbarHostState, tabModifier)
    }
}
