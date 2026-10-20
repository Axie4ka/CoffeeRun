package ru.coffeerun.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.theme.spacing

/** Лист выбора кофейни. Выбор сразу закрывает лист. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopPickerSheet(selected: Shop, onSelect: (Shop) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = MaterialTheme.spacing.large).padding(bottom = MaterialTheme.spacing.large)) {
            Text(stringResource(R.string.menu_choose_shop), style = MaterialTheme.typography.titleMedium)
            Shop.entries.forEach { shop ->
                RadioRow(
                    label = stringResource(shop.addressRes),
                    supporting = stringResource(R.string.shop_status),
                    selected = shop == selected,
                    onClick = {
                        onSelect(shop)
                        onDismiss()
                    }
                )
            }
        }
    }
}
