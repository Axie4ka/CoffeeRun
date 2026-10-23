package ru.coffeerun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.coffeerun.R
import ru.coffeerun.model.Shop
import ru.coffeerun.ui.components.ScreenTopBar
import ru.coffeerun.ui.components.ShopPickerSheet
import ru.coffeerun.ui.theme.CoffeeRunTheme
import ru.coffeerun.ui.theme.spacing
import ru.coffeerun.ui.util.ProvidePreviewNotifier
import ru.coffeerun.ui.util.snackbar

/** Профиль: данные пользователя, статистика и список настроек («Изменить» и «Телефон» пока заглушки). */
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onShopSelected: (Shop) -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (shop, ordersCount, favoritesCount) = uiState
    var showShopPicker by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val notify = MaterialTheme.snackbar
    val name = stringResource(R.string.profile_name)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.profile_title))
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Column(Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.profile_phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = notify::notifyPlaceholder) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.profile_edit))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            StatCard(ordersCount, stringResource(R.string.profile_orders_label), Modifier.weight(1f))
            StatCard(favoritesCount, stringResource(R.string.profile_favorites_label), Modifier.weight(1f))
        }
        Column(Modifier.padding(top = MaterialTheme.spacing.large)) {
            ProfileItem(Icons.Default.Favorite, R.string.profile_favorites, if (favoritesCount == 0) stringResource(R.string.profile_favorites_none)
                else pluralStringResource(R.plurals.profile_favorites_count, favoritesCount, favoritesCount), onFavoritesClick)
            ProfileItem(Icons.Default.LocationOn, R.string.profile_shop, stringResource(shop.addressRes)) { showShopPicker = true }
            ProfileItem(Icons.Default.Phone, R.string.profile_phone_title, stringResource(R.string.profile_phone), notify::notifyPlaceholder)
            HorizontalDivider()
            ProfileItem(Icons.Default.Info, R.string.profile_about, stringResource(R.string.profile_version)) { showAbout = true }
        }
    }
    if (showShopPicker) ShopPickerSheet(shop, onShopSelected, onDismiss = { showShopPicker = false })
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_version), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.about_text))
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileItem(icon: ImageVector, titleRes: Int, supporting: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Preview(showBackground = true, locale = "ru")
@Composable
private fun ProfileScreenPreview() {
    CoffeeRunTheme(dynamicColor = false) {
        ProvidePreviewNotifier { ProfileScreen(ProfileUiState(Shop.MOSKOVSKAYA, ordersCount = 2, favoritesCount = 3), {}, {}) }
    }
}
