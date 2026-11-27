package ru.coffeerun.data.repository

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.coffeerun.data.network.dto.ShopScheduleDto
import ru.coffeerun.domain.checkout.PickupHours
import ru.coffeerun.model.Shop

/** Расписание из последнего доступного снимка меню. */
class ShopScheduleBook {
    private data class Schedule(val zone: ZoneId, val days: Map<Int, PickupHours>)

    private var publishedAt = Long.MIN_VALUE
    private var schedules = emptyMap<Shop, Schedule>()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    @Synchronized
    fun publish(source: List<ShopScheduleDto>, fetchedAt: Long) {
        if (fetchedAt < publishedAt || source.isEmpty()) return
        val parsed = source.mapNotNull { incoming ->
            runCatching {
                val shop = Shop.valueOf(incoming.shop)
                val zone = ZoneId.of(incoming.timeZone)
                require(incoming.preparationMinutes in 1..120)
                val days = incoming.hours.flatMap { period ->
                    val hours = PickupHours(
                        zone, LocalTime.parse(period.closesAt), LocalTime.parse(period.opensAt), incoming.preparationMinutes
                    )
                    period.days.map { it to hours }
                }
                require(days.size == 7 && days.map { it.first }.toSet() == (1..7).toSet())
                shop to Schedule(zone, days.toMap())
            }.getOrNull()
        }.toMap()
        if (parsed.keys.containsAll(Shop.entries)) {
            publishedAt = fetchedAt
            if (schedules != parsed) {
                schedules = parsed
                _revision.value++
            }
        }
    }

    @Synchronized
    fun today(shop: Shop): PickupHours {
        val schedule = schedules[shop]
        if (schedule != null) return schedule.days.getValue(LocalDate.now(schedule.zone).dayOfWeek.value)
        return PickupHours(ZoneId.of("Europe/Moscow"), LocalTime.of(22, 0))
    }
}
