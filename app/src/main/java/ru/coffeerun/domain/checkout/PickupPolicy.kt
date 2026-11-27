package ru.coffeerun.domain.checkout

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.coffeerun.model.CLOSING_HOUR
import ru.coffeerun.model.READY_IN_MINUTES
import ru.coffeerun.model.Shop

/** Дневное расписание; null означает, что источник пока не сообщил час открытия. */
data class PickupHours(
    val zone: ZoneId,
    val closesAt: LocalTime,
    val opensAt: LocalTime? = null,
    val preparationMinutes: Long = READY_IN_MINUTES
) {
    init { require(opensAt == null || opensAt < closesAt) { "Ожидается дневное расписание" } }
}

class PickupPolicy(
    private val now: () -> Long = System::currentTimeMillis,
    // shortcut: API не предоставляет расписание; заменить демонстрационные часы данными кофейни при F05.
    private val hours: (Shop) -> PickupHours = { PickupHours(ZoneId.of("Europe/Moscow"), LocalTime.of(CLOSING_HOUR, 0)) },
    val scheduleRevision: Flow<Long> = flowOf(0L)
) {
    fun today(shop: Shop): PickupHours = hours(shop)

    fun zone(shop: Shop): ZoneId = today(shop).zone

    fun selectTime(shop: Shop, hour: Int, minute: Int): Long? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val schedule = hours(shop)
        val current = now()
        val day = Instant.ofEpochMilli(current).atZone(schedule.zone).toLocalDate()
        val selected = day.atTime(hour, minute).atZone(schedule.zone).toInstant().toEpochMilli()
        return selected.takeIf { isValid(schedule, current, selected) }
    }

    fun isValid(shop: Shop, pickupAtMillis: Long?): Boolean = isValid(hours(shop), now(), pickupAtMillis)

    fun earliestTime(shop: Shop): LocalTime? {
        val schedule = hours(shop)
        val current = now()
        val day = Instant.ofEpochMilli(current).atZone(schedule.zone).toLocalDate()
        val opening = schedule.opensAt?.let { day.atTime(it).atZone(schedule.zone).toInstant().toEpochMilli() }
        val earliest = maxOf(current, opening ?: current) + schedule.preparationMinutes * 60_000L
        val step = 5 * 60_000L
        val rounded = (earliest + step - 1) / step * step
        return rounded.takeIf { isValid(schedule, current, it) }
            ?.let { Instant.ofEpochMilli(it).atZone(schedule.zone).toLocalTime() }
    }

    private fun isValid(schedule: PickupHours, current: Long, pickupAtMillis: Long?): Boolean {
        val day = Instant.ofEpochMilli(current).atZone(schedule.zone).toLocalDate()
        val closing = day.atTime(schedule.closesAt).atZone(schedule.zone).toInstant().toEpochMilli()
        val opening = schedule.opensAt?.let { day.atTime(it).atZone(schedule.zone).toInstant().toEpochMilli() }
        if (pickupAtMillis == null && opening != null && current < opening) return false
        val earliest = maxOf(current, opening ?: current) + schedule.preparationMinutes * 60_000L
        val selected = pickupAtMillis ?: (current + schedule.preparationMinutes * 60_000L)
        return Instant.ofEpochMilli(selected).atZone(schedule.zone).toLocalDate() == day &&
            selected >= earliest && selected <= closing
    }
}
