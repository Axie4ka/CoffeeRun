package ru.coffeerun.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R

/**
 * Верхняя панель 64 dp с заголовком экрана; кнопка «назад» показывается, если передан [onBack].
 * Заголовок — headlineSmall (Yeseva One 24): это бренд-момент экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        }
    )
}
