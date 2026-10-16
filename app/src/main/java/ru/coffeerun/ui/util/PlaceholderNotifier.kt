package ru.coffeerun.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Заглушка для ещё не реализованных действий (назад, избранное, корзина и т.п.):
 * вместо реального действия показывает пользователю сообщение «не реализовано».
 */
fun interface PlaceholderNotifier {
    fun notifyPlaceholder()
}

// Значения по умолчанию нет намеренно: без провайдера приложение упадёт сразу,
// а не будет молча игнорировать нажатия.
val LocalPlaceholderNotifier = staticCompositionLocalOf<PlaceholderNotifier> {
    error("Notifications 'Not Implemented' were not Provided")
}

/** Показывает снекбар с сообщением [notImplementedMessage]. */
fun CoroutineScope.showNotImplemented(
    snackbarHostState: SnackbarHostState,
    notImplementedMessage: String
) {
    launch {
        // Снекбары показываются по очереди. Закрываем текущий, чтобы при частых
        // нажатиях одинаковые сообщения не копились и не шли друг за другом.
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = notImplementedMessage,
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
    }
}

/**
 * Короткий доступ к [LocalPlaceholderNotifier] в стиле `MaterialTheme.colorScheme`:
 * `MaterialTheme.snackbar::notifyPlaceholder`.
 */
val MaterialTheme.snackbar: PlaceholderNotifier
    @Composable @ReadOnlyComposable get() = LocalPlaceholderNotifier.current