package ru.coffeerun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.ui.theme.spacing

/**
 * Вкладка, которой нужен аккаунт, у гостя: заголовок экрана и по центру карточка
 * «Войдите…» с кнопкой (M3 filled card). Используют «Профиль» и «Заказы».
 */
@Composable
fun SignInPrompt(
    screenTitle: String,
    title: String,
    text: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = screenTitle)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Высота верхней панели: карточка по центру экрана, а не области под панелью
                .padding(start = MaterialTheme.spacing.large, end = MaterialTheme.spacing.large, bottom = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.extraSmall)) {
                        Text(stringResource(R.string.auth_login_button))
                    }
                }
            }
        }
    }
}
