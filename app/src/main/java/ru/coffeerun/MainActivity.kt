package ru.coffeerun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import ru.coffeerun.ui.screens.DrinkScreen
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.util.LocalPlaceholderNotifier
import ru.coffeerun.ui.util.PlaceholderNotifier
import ru.coffeerun.ui.util.showNotImplemented
import ru.coffeerun.ui.util.snackbar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoffeeRunTheme() {
                // Количество товаров в корзине. Хранится здесь, а не в DrinkScreen,
                // потому что корзина — общее состояние приложения, а не одного экрана
                var cartCount by rememberSaveable { mutableIntStateOf(0) }

                // Один SnackbarHostState на заглушки и на экран: сообщение
                // «не реализовано» показывается в снекбаре самого экрана
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                // Строку читаем здесь: stringResource доступен только в композиции,
                // а внутри обработчика нажатия его вызвать нельзя
                val notImplementedMessage = stringResource(R.string.notice_not_implemented)
                val placeholderNotifier = remember(notImplementedMessage, snackbarHostState) {
                    PlaceholderNotifier {
                        scope.showNotImplemented(snackbarHostState, notImplementedMessage)
                    }
                }

                CompositionLocalProvider(LocalPlaceholderNotifier provides placeholderNotifier) {
                    DrinkScreen(
                        cartCount = cartCount,
                        // Навигации и остальных экранов пока нет — на этих
                        // действиях стоят заглушки
                        onBack = MaterialTheme.snackbar::notifyPlaceholder,
                        onFavorite = MaterialTheme.snackbar::notifyPlaceholder,
                        onShare = MaterialTheme.snackbar::notifyPlaceholder,
                        onCart = MaterialTheme.snackbar::notifyPlaceholder,
                        onAddToCart = { amount -> cartCount += amount },
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}

