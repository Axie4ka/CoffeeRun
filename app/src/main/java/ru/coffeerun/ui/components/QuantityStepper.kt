package ru.coffeerun.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.ui.theme.spacing

/**
 * Счётчик «− N +» стандартного размера 40 dp — общий для экрана позиции и корзины.
 * Границы задаёт вызывающий экран через [canDecrease] / [canIncrease].
 */
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(enabled = canDecrease, onClick = onDecrease) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.action_decrease))
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            // Минимальная ширина, чтобы кнопки не сдвигались при переходе 9 → 10
            modifier = Modifier.widthIn(min = 32.dp)
        )
        StepButton(enabled = canIncrease, onClick = onIncrease) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
        }
    }
}

@Composable
private fun StepButton(enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        // У выключенной кнопки M3 только 12 % заливки — на тёмном фоне она теряется, поэтому обводка
        modifier = if (enabled) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    ) { icon() }
}
