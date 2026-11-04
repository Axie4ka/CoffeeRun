package ru.coffeerun.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.ui.auth.LoginScreen
import ru.coffeerun.ui.auth.LoginViewModel
import ru.coffeerun.ui.auth.RegisterScreen
import ru.coffeerun.ui.auth.RegisterViewModel
import ru.coffeerun.ui.cart.CartScreen
import ru.coffeerun.ui.cart.CartViewModel
import ru.coffeerun.ui.checkout.CheckoutScreen
import ru.coffeerun.ui.checkout.CheckoutViewModel
import ru.coffeerun.ui.checkout.OrderPlacedScreen
import ru.coffeerun.ui.checkout.OrderPlacedViewModel
import ru.coffeerun.ui.components.RetryMessage
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.components.Tab
import ru.coffeerun.ui.menu.MenuScreen
import ru.coffeerun.ui.menu.MenuViewModel
import ru.coffeerun.ui.orders.OrderDetailsScreen
import ru.coffeerun.ui.orders.OrderDetailsViewModel
import ru.coffeerun.ui.orders.OrdersScreen
import ru.coffeerun.ui.orders.OrdersViewModel
import ru.coffeerun.ui.product.ProductScreen
import ru.coffeerun.ui.product.ProductUiState
import ru.coffeerun.ui.product.ProductViewModel
import ru.coffeerun.ui.profile.EditProfileScreen
import ru.coffeerun.ui.profile.EditProfileViewModel
import ru.coffeerun.ui.profile.FavoritesScreen
import ru.coffeerun.ui.profile.FavoritesViewModel
import ru.coffeerun.ui.profile.ProfileScreen
import ru.coffeerun.ui.profile.ProfileViewModel

/**
 * Граф экранов: каждому маршруту — его ViewModel и экран. Навигация живёт здесь, экраны о ней не знают.
 * [tabModifier] — отступы внешнего Scaffold, они нужны только вкладкам.
 */
@Composable
fun CoffeeRunNavHost(
    navController: NavHostController,
    isLoggedIn: Boolean?,
    snackbarHostState: SnackbarHostState,
    tabModifier: Modifier
) {
    // Для снэкбаров, которые должны пережить закрытие экрана (например, «Профиль сохранён»)
    val appScope = rememberCoroutineScope()
    NavHost(
        navController,
        startDestination = MenuRoute,
        // Вкладки сменяются через fade; экраны поверх вкладок выезжают справа и уезжают обратно по «назад»
        enterTransition = { if (targetState.isTab()) fadeIn(tween(TAB_FADE_MS)) else slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { fadeOut(tween(if (targetState.isTab()) TAB_FADE_MS else SLIDE_MS)) },
        popEnterTransition = { fadeIn(tween(if (initialState.isTab()) TAB_FADE_MS else SLIDE_MS)) },
        popExitTransition = { if (initialState.isTab()) fadeOut(tween(TAB_FADE_MS)) else slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS)) }
    ) {
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
                // Гостя — сначала на вход, после входа оформление откроется само
                onCheckout = { navController.navigate(if (isLoggedIn == true) CheckoutRoute else LoginRoute(toCheckout = true)) },
                onBrowseMenu = { navController.openTab(Tab.MENU) },
                modifier = tabModifier
            )
        }

        composable<OrdersRoute> {
            val vm: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)
            val orders by vm.orders.collectAsStateWithLifecycle()
            val load by vm.load.collectAsStateWithLifecycle()
            val cancelling by vm.cancelling.collectAsStateWithLifecycle()
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // Каждый показ вкладки: переход с другой вкладки, возврат со входа или с «Заказ принят»
            LaunchedEffect(Unit) { vm.onShown() }
            LaunchedEffect(message) {
                message?.let {
                    // Сначала показать, потом сбросить: сброс меняет ключ эффекта и отменил бы снэкбар
                    snackbarHostState.showSnackbar(context.getString(it))
                    vm.onMessageShown()
                }
            }
            OrdersScreen(
                orders = orders,
                load = load,
                onRetry = vm::retry,
                onRepeat = {
                    vm.repeat(it)
                    navController.openTab(Tab.CART)
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
            val order by vm.order.collectAsStateWithLifecycle()
            val load by vm.load.collectAsStateWithLifecycle()
            val cancelling by vm.cancelling.collectAsStateWithLifecycle()
            val notFound by vm.notFound.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val context = LocalContext.current
            // Такого заказа на сервере нет — смотреть нечего
            LaunchedEffect(notFound) { if (notFound) navController.popBackStack<OrderDetailsRoute>(inclusive = true) }
            LaunchedEffect(message) {
                message?.let {
                    snackbarHostState.showSnackbar(context.getString(it))
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
                    vm.repeat()
                    navController.jumpToTab(Tab.CART)
                },
                snackbarHostState = snackbarHostState
            )
        }

        composable<ProfileRoute> { entry ->
            val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
            val profileState by vm.uiState.collectAsStateWithLifecycle()
            // Профиль сохранили на экране «Изменить» — показываем то, что теперь на сервере
            val saved by entry.savedStateHandle.getStateFlow(PROFILE_SAVED, false).collectAsStateWithLifecycle()
            LaunchedEffect(saved) {
                if (saved) {
                    vm.retry()
                    entry.savedStateHandle[PROFILE_SAVED] = false
                }
            }
            ProfileScreen(
                uiState = profileState,
                onShopSelected = vm::selectShop,
                onFavoritesClick = { navController.navigate(FavoritesRoute) },
                onLogin = { navController.navigate(LoginRoute()) },
                onLogout = vm::logout,
                onRetry = vm::retry,
                onEdit = { navController.navigate(EditProfileRoute(it.name, it.phone)) },
                modifier = tabModifier
            )
        }

        composable<EditProfileRoute> {
            val vm: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val savedText = stringResource(R.string.profile_saved)
            LaunchedEffect(state.isDone) {
                if (state.isDone) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(PROFILE_SAVED, true)
                    navController.popBackStack()
                    // Снэкбар живёт дольше экрана: показываем его уже на профиле
                    appScope.launch { snackbarHostState.showSnackbar(savedText) }
                }
            }
            EditProfileScreen(
                uiState = state,
                onNameChange = vm::onNameChange,
                onPhoneChange = vm::onPhoneChange,
                onSave = vm::save,
                onMessageShown = vm::onMessageShown,
                onBack = dropUnlessResumed { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }

        composable<LoginRoute> { entry ->
            val toCheckout = entry.toRoute<LoginRoute>().toCheckout
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            // Вошли — туда, куда шли: на оформление или назад
            LaunchedEffect(state.isDone) { if (state.isDone) navController.afterLogin(toCheckout) }
            LoginScreen(
                uiState = state,
                onPhoneChange = vm::onPhoneChange,
                onPasswordChange = vm::onPasswordChange,
                onSubmit = vm::submit,
                onMessageShown = vm::onMessageShown,
                onRegister = dropUnlessResumed { navController.navigate(RegisterRoute(toCheckout)) },
                onBack = dropUnlessResumed { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }

        composable<RegisterRoute> { entry ->
            val toCheckout = entry.toRoute<RegisterRoute>().toCheckout
            val vm: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            // Зарегистрировались — как после входа; регистрация и вход под ней закрываются оба
            LaunchedEffect(state.isDone) { if (state.isDone) navController.afterLogin(toCheckout) }
            RegisterScreen(
                uiState = state,
                onNameChange = vm::onNameChange,
                onPhoneChange = vm::onPhoneChange,
                onPasswordChange = vm::onPasswordChange,
                onSubmit = vm::submit,
                onMessageShown = vm::onMessageShown,
                // Регистрация открывается только со входа: «Войти» — вернуться к нему, а не открыть второй
                onLogin = dropUnlessResumed { navController.popBackStack() },
                onBack = dropUnlessResumed { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }

        composable<FavoritesRoute> {
            val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
            val favoritesState by vm.uiState.collectAsStateWithLifecycle()
            FavoritesScreen(
                uiState = favoritesState,
                onProductClick = { navController.navigate(ProductRoute(it)) },
                onBrowseMenu = { navController.jumpToTab(Tab.MENU) },
                onBack = { navController.popBackStack() },
                onRetry = vm::retry
            )
        }

        composable<ProductRoute> {
            val vm: ProductViewModel = viewModel(factory = ProductViewModel.Factory)
            val uiState by vm.uiState.collectAsStateWithLifecycle()
            // Сервер не принял сердечко — оно уже откатилось, объясняем почему
            val message by vm.message.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(message) {
                message?.let {
                    // Сначала показать, потом сбросить: сброс меняет ключ эффекта и отменил бы снэкбар
                    snackbarHostState.showSnackbar(context.getString(it))
                    vm.onMessageShown()
                }
            }
            when (val state = uiState) {
                ProductUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ProductUiState.NotFound -> LaunchedEffect(Unit) { navController.popBackStack<ProductRoute>(inclusive = true) }
                // Без шапки экрана позиции некуда нажать «назад», поэтому своя панель с одной стрелкой
                ProductUiState.Error -> Column(Modifier.fillMaxSize()) {
                    ScreenTopBar(title = "", onBack = { navController.popBackStack() })
                    RetryMessage(stringResource(R.string.load_error), vm::retry)
                }
                is ProductUiState.Success -> {
                    val product = state.product
                    val name = product.name
                    val price = stringResource(
                        if (product.isPriceFrom) R.string.price_from else R.string.price_rub,
                        product.price
                    )
                    val shareText = stringResource(R.string.share_product, name, price)
                    ProductScreen(
                        product = state.product,
                        form = state.form,
                        unitPrice = state.unitPrice,
                        prices = state.prices,
                        cartCount = state.cartCount,
                        onSizeSelected = vm::selectSize,
                        onMilkSelected = vm::selectMilk,
                        onIcedChange = vm::setIced,
                        onExtraToggle = vm::toggleExtra,
                        onNoteChange = vm::setNote,
                        onQuantityChange = vm::changeQuantity,
                        // Добавили — назад, откуда пришли (меню или избранное). dropUnlessResumed не даст
                        // второму нажатию во время перехода добавить ещё раз и снять со стека само меню
                        onAddToCart = dropUnlessResumed {
                            vm.addToCart()
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                        isFavorite = state.isFavorite,
                        // Избранное — данные аккаунта: гостя на вход, после него он вернётся на позицию
                        onFavorite = { if (isLoggedIn == true) vm.toggleFavorite() else navController.navigate(LoginRoute()) },
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
            val checkoutState by vm.uiState.collectAsStateWithLifecycle()
            val placedOrderNumber = checkoutState.placedOrderNumber
            LaunchedEffect(placedOrderNumber) {
                placedOrderNumber?.let { number ->
                    // Cart и Checkout снимаем: «назад» с «Заказ принят» ведёт в меню
                    navController.navigate(PlacedRoute(number)) { popUpTo(MenuRoute) }
                    vm.onPlacedOrderShown()
                }
            }
            CheckoutScreen(
                uiState = checkoutState,
                onBack = { navController.popBackStack() },
                onConfirm = vm::placeOrder,
                onPickupAsap = vm::pickupAsap,
                onPickupTimeChosen = vm::setPickupTime,
                onShopSelected = vm::selectShop,
                onMessageShown = vm::onMessageShown,
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

private const val TAB_FADE_MS = 200
private const val SLIDE_MS = 300

private fun NavBackStackEntry.isTab() = Tab.entries.any { destination.hasRoute(it.route::class) }

/** Ключ результата «профиль сохранён» в SavedStateHandle профиля. */
private const val PROFILE_SAVED = "profileSaved"

/** После входа или регистрации: закрыть вход (и регистрацию над ним) и, если гость шёл оформлять, открыть оформление. */
private fun NavController.afterLogin(toCheckout: Boolean) {
    if (toCheckout) navigate(CheckoutRoute) { popUpTo<LoginRoute> { inclusive = true } }
    else popBackStack<LoginRoute>(inclusive = true)
}

/** Переключение вкладок с сохранением их состояния — для нижней панели и кнопок «В меню». */
internal fun NavController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    // Меню — стартовый экран и со стека не снимается. Под его ключом navigation сохраняет
    // только что снятую вкладку, и restoreState вернул бы её вместо меню
    restoreState = tab != Tab.MENU
}

/**
 * Переход на вкладку с экрана поверх вкладок: этот экран закрываем, а не сохраняем,
 * иначе вкладка «Меню» потом восстановит его через restoreState.
 */
private fun NavController.jumpToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(MenuRoute)
    launchSingleTop = true
}
