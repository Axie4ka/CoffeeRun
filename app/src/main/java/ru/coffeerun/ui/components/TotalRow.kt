package ru.coffeerun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.ui.theme.priceLarge

/** Строка «Итого» с крупной суммой — общая для корзины и оформления. */
@Composable
fun TotalRow(
    total: Int,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.total_label)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.price_rub, total),
            style = MaterialTheme.typography.priceLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
