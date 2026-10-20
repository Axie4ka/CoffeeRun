package ru.coffeerun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import ru.coffeerun.ui.CoffeeRunApp
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.util.LocalPlaceholderNotifier
import ru.coffeerun.ui.util.PlaceholderNotifier
import ru.coffeerun.ui.util.showNotImplemented

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoffeeRunTheme {
                // Один SnackbarHostState на заглушки и на экраны: сообщение
                // «не реализовано» показывается в снекбаре текущего экрана
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
                    CoffeeRunApp(snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}
