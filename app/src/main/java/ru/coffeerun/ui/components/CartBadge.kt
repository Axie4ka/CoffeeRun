package ru.coffeerun.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Бейдж с числом порций на значке корзины — для `BadgedBox(badge = { CartBadge(count) })`.
 * Цвета фиксированных ролей: одинаковые в светлой и тёмной теме. Пустую корзину не отмечает.
 * Когда порций становится больше, бейдж подпрыгивает: видно, что добавилось, не открывая корзину.
 */
@Composable
fun CartBadge(count: Int) {
    // Снаружи if: первое добавление в пустую корзину тоже должно подпрыгнуть
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > shown) {
            scale.animateTo(1.4f, tween(durationMillis = 120))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        shown = count
    }
    if (count > 0) {
        Badge(
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
            containerColor = MaterialTheme.colorScheme.tertiaryFixedDim,
            contentColor = MaterialTheme.colorScheme.onTertiaryFixed
        ) { Text(count.toString()) }
    }
}
