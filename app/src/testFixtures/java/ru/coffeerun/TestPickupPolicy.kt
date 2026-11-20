package ru.coffeerun

import java.time.Instant
import ru.coffeerun.domain.checkout.PickupPolicy

/** Фиксированный полдень кофейни: тесты покупки не зависят от часа запуска. */
fun testPickupPolicy(now: () -> Long = { Instant.parse("2026-11-10T09:00:00Z").toEpochMilli() }) = PickupPolicy(now = now)
