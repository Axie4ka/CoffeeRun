package ru.coffeerun.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ru.coffeerun.ui.auth.LoginScreen
import ru.coffeerun.ui.auth.LoginViewModel
import ru.coffeerun.ui.auth.RegisterScreen
import ru.coffeerun.ui.auth.RegisterViewModel
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.authGraph(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
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
}

/** После входа или регистрации: закрыть вход (и регистрацию над ним) и, если гость шёл оформлять, открыть оформление. */
private fun NavController.afterLogin(toCheckout: Boolean) {
    if (toCheckout) navigate(CheckoutRoute) { popUpTo<LoginRoute> { inclusive = true } }
    else popBackStack<LoginRoute>(inclusive = true)
}
