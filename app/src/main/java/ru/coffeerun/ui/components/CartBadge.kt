package ru.coffeerun.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Бейдж с числом порций на значке корзины — для `BadgedBox(badge = { CartBadge(count) })`.
 * Цвета фиксированных ролей: одинаковые в светлой и тёмной теме. Пустую корзину не отмечает.
 */
@Composable
fun CartBadge(count: Int) {
    if (count > 0) {
        Badge(
            containerColor = MaterialTheme.colorScheme.tertiaryFixedDim,
            contentColor = MaterialTheme.colorScheme.onTertiaryFixed
        ) { Text(count.toString()) }
    }
}
