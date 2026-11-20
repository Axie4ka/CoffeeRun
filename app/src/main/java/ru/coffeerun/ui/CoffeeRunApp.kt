package ru.coffeerun.ui

import ru.coffeerun.ui.util.OnLocale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.coffeerun.ui.components.AppNavigationBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.components.RetryMessage
import ru.coffeerun.R
import ru.coffeerun.ui.auth.SessionViewModel
import ru.coffeerun.ui.cart.CartViewModel
import ru.coffeerun.ui.navigation.CoffeeRunNavHost
import ru.coffeerun.ui.navigation.openTab
import ru.coffeerun.ui.navigation.MenuRoute
import kotlinx.coroutines.flow.filterNotNull

/**
 * Корень приложения: [CoffeeRunNavHost] внутри одного Scaffold. Нижняя панель видна только
 * на вкладках; Product, Checkout и Placed открываются поверх них.
 * У каждого экрана своя ViewModel (живёт, пока экран в стеке); данные — в общих
 * репозиториях из [ru.coffeerun.AppContainer]. Граф экранов и переходы — в [CoffeeRunNavHost].
 */
@Composable
fun CoffeeRunApp(snackbarHostState: SnackbarHostState) {
    val sessionVm = viewModel<SessionViewModel>(factory = SessionViewModel.Factory)
    val isLoggedIn by sessionVm.isLoggedIn.collectAsStateWithLifecycle()
    val readFailed by sessionVm.readFailed.collectAsStateWithLifecycle()
    if (isLoggedIn == null || readFailed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (readFailed) RetryMessage(stringResource(R.string.session_read_error), sessionVm::retrySessionRead)
            else CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    // destination.route — строка, а Tab.route — объект, поэтому сравниваем по классу маршрута
    val currentTab = Tab.entries.find { tab ->
        entry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    // Вне NavHost — ViewModel уровня Activity, только ради счётчика на нижней панели
    val cartVm = viewModel<CartViewModel>(factory = CartViewModel.Factory)
    val cartUiState by cartVm.uiState.collectAsStateWithLifecycle()
    // Здесь, а не на экране корзины: язык могут сменить, пока открыта любая вкладка
    OnLocale(cartVm::onLocale)
    var navigationGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var navigationLoggedIn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionVm, navController) {
        sessionVm.sessions.filterNotNull().collect { current ->
            if (navigationGeneration != null && navigationLoggedIn &&
                (navigationGeneration != current.generation || !current.isLoggedIn)) {
                Tab.entries.forEach { navController.clearBackStack(it.route) }
                navController.navigate(MenuRoute) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            navigationGeneration = current.generation
            navigationLoggedIn = current.isLoggedIn
        }
    }

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
