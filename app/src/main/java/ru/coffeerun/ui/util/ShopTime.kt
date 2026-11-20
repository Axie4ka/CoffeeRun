package ru.coffeerun.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.ZoneId
import ru.coffeerun.domain.checkout.PickupPolicy
import ru.coffeerun.model.Shop

/** Общий пояс кофейни для оформления, подтверждения и истории. Default нужен превью. */
val LocalShopZone = staticCompositionLocalOf<(Shop) -> ZoneId> { PickupPolicy()::zone }
