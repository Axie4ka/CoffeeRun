package ru.coffeerun.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.coffeerun.domain.checkout.PickupHours
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import ru.coffeerun.R
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.model.Shop

/** Общий пояс кофейни для оформления, подтверждения и истории. Default нужен превью. */
val LocalShopZone = staticCompositionLocalOf<(Shop) -> ZoneId> { PickupPolicy()::zone }
val LocalPickupPolicy = staticCompositionLocalOf { PickupPolicy() }

@Composable
fun currentPickupHours(shop: Shop): PickupHours {
    val policy = LocalPickupPolicy.current
    val revision by policy.scheduleRevision.collectAsStateWithLifecycle(0L)
    return androidx.compose.runtime.remember(policy, shop, revision, java.time.LocalDate.now(policy.zone(shop))) {
        policy.today(shop)
    }
}

@Composable
fun shopHoursText(shop: Shop): String {
    val hours = currentPickupHours(shop)
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(LocalConfiguration.current.locales[0])
    val closes = hours.closesAt.format(formatter)
    return hours.opensAt?.let { stringResource(R.string.shop_today_hours, it.format(formatter), closes) }
        ?: stringResource(R.string.shop_today_closes, closes)
}
