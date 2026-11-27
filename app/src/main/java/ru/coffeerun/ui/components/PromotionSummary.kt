package ru.coffeerun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R

@Composable
fun PromotionSummary(total: Int, discount: Long) {
    if (discount <= 0) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.promotion_subtotal))
        Text(stringResource(R.string.price_rub, total + discount))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.promotion_discount))
        Text(
            stringResource(R.string.promotion_discount_amount, discount),
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
