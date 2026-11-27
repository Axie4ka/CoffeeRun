package ru.coffeerun.ui.navigation

import ru.coffeerun.ui.util.OnLocale
import ru.coffeerun.ui.components.sharedProductImage
import ru.coffeerun.ui.components.LocalSharedTransitionScope
import ru.coffeerun.ui.components.LocalNavAnimatedScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedContentScope
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.model.DrinkSize
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
import ru.coffeerun.ui.profile.RecipesRoute

/**
 * Граф экранов: каждому маршруту — его ViewModel и экран. Навигация живёт здесь, экраны о ней не знают.
 * [tabModifier] — отступы внешнего Scaffold, они нужны только вкладкам.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CoffeeRunNavHost(
    navController: NavHostController,
    isLoggedIn: Boolean?,
    snackbarHostState: SnackbarHostState,
    tabModifier: Modifier
) {
    // Для снэкбаров, которые должны пережить закрытие экрана (например, «Профиль сохранён»)
    val appScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val showShopError: () -> Unit = {
        appScope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.shop_storage_unavailable)) }
    }
    // Сцена для общих элементов: фото позиции перелетает между меню, избранным и экраном напитка
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController,
        startDestination = MenuRoute,
        // Вкладки сменяются через fade; экраны поверх вкладок выезжают справа и уезжают обратно по «назад»
        enterTransition = { if (targetState.isTab()) fadeIn(tween(TAB_FADE_MS)) else slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { fadeOut(tween(if (targetState.isTab()) TAB_FADE_MS else SLIDE_MS)) },
        popEnterTransition = { fadeIn(tween(if (initialState.isTab()) TAB_FADE_MS else SLIDE_MS)) },
        popExitTransition = { if (initialState.isTab()) fadeOut(tween(TAB_FADE_MS)) else slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS)) }
    ) {
        composable<MenuRoute> { entry ->
            ProvideNavScope {
                val vm: MenuViewModel = viewModel(factory = MenuViewModel.Factory)
                OnLocale(vm::onLocale)
                val menuState by vm.uiState.collectAsStateWithLifecycle()
                val menuRefresh by vm.refreshState.collectAsStateWithLifecycle()
                val shop by vm.shop.collectAsStateWithLifecycle()
                val showPastry by entry.savedStateHandle.getStateFlow(SHOW_PASTRY, false).collectAsStateWithLifecycle()
                val orderVm: OrdersViewModel = viewModel(factory = OrdersViewModel.Factory)
                val orders by orderVm.orders.collectAsStateWithLifecycle()
                val active = if (isLoggedIn == true) orders.filter { it.isActive } else emptyList()
                LaunchedEffect(isLoggedIn) { if (isLoggedIn == true) orderVm.onShown() }
                MenuScreen(
                    uiState = menuState,
                    refreshState = menuRefresh,
                    shop = shop,
                    activeOrder = active.maxByOrNull { it.placedAtMillis },
                    multipleActiveOrders = active.size > 1,
                    onActiveOrder = { navController.navigate(OrderDetailsRoute(it.number)) },
                    onAllOrders = { navController.openTab(Tab.ORDERS) },
                    onPromotedProduct = { navController.navigate(ProductRoute(it, size = DrinkSize.MEDIUM.name)) },
                    showPastry = showPastry,
                    onPastryShown = { entry.savedStateHandle[SHOW_PASTRY] = false },
                    onShopSelected = { vm.selectShop(it, showShopError) },
                    onRetry = vm::retry,
                    onProductClick = { navController.navigate(ProductRoute(it.id)) },
                    onAddProduct = { product ->
                        vm.quickAdd(product) { error ->
                            if (error != null) appScope.launch { snackbarHostState.showSnackbar(resources.getString(error)) }
                        }
                    },
                    modifier = tabModifier
                )
            }
        }

        composable<CartRoute> {
            val vm: CartViewModel = viewModel(factory = CartViewModel.Factory)
            val cartState by vm.uiState.collectAsStateWithLifecycle()
            val storageFailed by vm.storageFailed.collectAsStateWithLifecycle()
            val removedLine by vm.removedLine.collectAsStateWithLifecycle()
            val restoreLimit by vm.restoreLimit.collectAsStateWithLifecycle()
            OnLocale(vm::onLocale)
            LaunchedEffect(Unit) { vm.onShown() }
            LaunchedEffect(removedLine) {
                val removal = removedLine ?: return@LaunchedEffect
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.cart_removed),
                    actionLabel = resources.getString(R.string.cart_restore),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
                vm.finishRemoval(removal, result == SnackbarResult.ActionPerformed)
            }
            LaunchedEffect(restoreLimit) {
                if (restoreLimit) {
                    snackbarHostState.showSnackbar(resources.getString(R.string.cart_restore_limit))
                    vm.onRestoreLimitShown()
                }
            }
            LaunchedEffect(storageFailed) {
                if (storageFailed) {
                    snackbarHostState.showSnackbar(resources.getString(R.string.cart_storage_unavailable))
                    vm.onStorageErrorShown()
                }
            }
            CartScreen(
                uiState = cartState,
                onChangeQuantity = vm::changeQuantity,
                onRemove = vm::remove,
                onEdit = { navController.navigate(ProductRoute(it.product.id, cartLineId = it.id)) },
                onAddRecommendation = {
                    vm.addRecommendation {
                        appScope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.cart_add_limit)) }
                    }
                },
                onBrowsePastry = {
                    navController.openTab(Tab.MENU)
                    navController.currentBackStackEntry?.savedStateHandle?.set(SHOW_PASTRY, true)
                },
                // Гостя — сначала на вход, после входа оформление откроется само
                onCheckout = { navController.navigate(if (isLoggedIn == true) CheckoutRoute else LoginRoute(toCheckout = true)) },
                onBrowseMenu = { navController.openTab(Tab.MENU) },
                onRetryRead = vm::retryRead,
                modifier = tabModifier
            )
        }

        ordersGraph(navController, snackbarHostState, isLoggedIn, tabModifier, appScope)

        composable<ProfileRoute> {
            val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
            val profileState by vm.uiState.collectAsStateWithLifecycle()
            val logoutState by vm.logoutState.collectAsStateWithLifecycle()
            if (logoutState == ru.coffeerun.ui.profile.LogoutState.Failed) {
                RetryMessage(stringResource(R.string.profile_logout_failed), vm::logout, tabModifier.fillMaxSize())
            } else ProfileScreen(
                uiState = profileState,
                onShopSelected = { vm.selectShop(it, showShopError) },
                onFavoritesClick = { navController.navigate(FavoritesRoute) },
                onLogin = { navController.navigate(LoginRoute()) },
                onLogout = vm::logout,
                onRetry = vm::retry,
                onEdit = { navController.navigate(EditProfileRoute) },
                modifier = tabModifier
            )
        }

        composable<EditProfileRoute> {
            val vm: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory)
            val state by vm.uiState.collectAsStateWithLifecycle()
            val savedText = stringResource(R.string.profile_saved)
            LaunchedEffect(state.isDone) {
                if (state.isDone) {
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
                onRetryLoad = vm::load,
                onAvatarPicked = vm::onAvatarPicked,
                onAvatarRemoved = vm::onAvatarRemoved,
                onAvatarRetry = vm::retryAvatar,
                onBack = dropUnlessResumed { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }

        authGraph(navController, snackbarHostState, appScope)

        composable<FavoritesRoute> {
            ProvideNavScope {
                val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
                OnLocale(vm::onLocale)
                val favoritesState by vm.uiState.collectAsStateWithLifecycle()
                FavoritesScreen(
                    uiState = favoritesState,
                    onProductClick = { navController.navigate(ProductRoute(it)) },
                    onBrowseMenu = { navController.jumpToTab(Tab.MENU) },
                    onBack = { navController.popBackStack() },
                    onRetry = vm::retry,
                    recipes = {
                        RecipesRoute(
                            onEditRecipe = { navController.navigate(ProductRoute(it.productId, recipeId = it.id)) },
                            onBrowseMenu = { navController.jumpToTab(Tab.MENU) },
                            snackbarHostState = snackbarHostState
                        )
                    },
                    onRemoveMissing = { id ->
                        vm.removeMissing(id) {
                            appScope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.favorites_toggle_error)) }
                        }
                    }
                )
            }
        }

        composable<ProductRoute> { entry ->
            ProvideNavScope {
                val vm: ProductViewModel = viewModel(factory = ProductViewModel.Factory)
                OnLocale(vm::onLocale)
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                // Сервер не принял сердечко — оно уже откатилось, объясняем почему
                val message by vm.message.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val resources = LocalResources.current
                LaunchedEffect(message) {
                    message?.let {
                        // Сначала показать, потом сбросить: сброс меняет ключ эффекта и отменил бы снэкбар
                        snackbarHostState.showSnackbar(resources.getString(it))
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
                            totalPrice = state.totalPrice,
                            unitDiscount = state.discount,
                            prices = state.prices,
                            cartCount = state.cartCount,
                            recipeName = state.recipeName,
                            onSaveRecipe = if (isLoggedIn == true && vm.recipeSavingEnabled) { name ->
                                vm.saveRecipe(name) {
                                    if (vm.isRecipeEdit && navController.currentBackStackEntry === entry) navController.popBackStack()
                                }
                            } else null,
                            onRecipeLogin = if (isLoggedIn == false) {
                                { navController.navigate(LoginRoute()) }
                            } else null,
                            isCartEdit = vm.isCartEdit,
                            optionsAdjusted = state.optionsAdjusted,
                            onSizeSelected = vm::selectSize,
                            onMilkSelected = vm::selectMilk,
                            onIcedChange = vm::setIced,
                            onExtraToggle = vm::toggleExtra,
                            onNoteChange = vm::setNote,
                            onQuantityChange = vm::changeQuantity,
                            // Добавили — назад, откуда пришли (меню или избранное). dropUnlessResumed не даст
                            // второму нажатию во время перехода добавить ещё раз и снять со стека само меню
                            onAddToCart = dropUnlessResumed {
                                vm.addToCart { error ->
                                    if (error == null) {
                                        if (navController.currentBackStackEntry === entry) navController.popBackStack()
                                    } else appScope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(error))
                                    }
                                }
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
                onConfirm = { vm.placeOrder(checkoutState.quote, checkoutState.lines) },
                onCheckOrderHistory = { navController.jumpToTab(Tab.ORDERS) },
                onRecoverOrder = vm::recoverOrder,
                onAllowNewOrder = vm::allowNewOrder,
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
    }
}

private const val TAB_FADE_MS = 200
private const val SLIDE_MS = 300

/** Даёт экрану его анимацию входа и выхода — для общих элементов ([sharedProductImage]). */
@Composable
private fun AnimatedContentScope.ProvideNavScope(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)

private fun NavBackStackEntry.isTab() = Tab.entries.any { destination.hasRoute(it.route::class) }

/** Переключение вкладок с сохранением их состояния — для нижней панели и кнопок «В меню». */
private const val SHOW_PASTRY = "showPastry"

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
internal fun NavController.jumpToTab(tab: Tab) = navigate(tab.route) {
    popUpTo(MenuRoute)
    launchSingleTop = true
}
