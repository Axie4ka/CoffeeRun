package ru.coffeerun.domain.checkout

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import ru.coffeerun.model.Shop

class PickupPolicyTest {
    private val shop = Shop.MOSKOVSKAYA
    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(time: String) = Instant.parse("2026-11-10T${time}:00Z").toEpochMilli()
    private fun policy(time: String, opening: LocalTime? = null) = PickupPolicy(
        now = { at(time) }, hours = { PickupHours(zone, LocalTime.of(22, 0), opening) }
    )

    @Test fun scheduledBoundaries_andInvalidPickerInput() {
        val p = policy("09:00") // 12:00 Moscow
        assertEquals(at("09:15"), p.selectTime(shop, 12, 15))
        assertEquals(at("19:00"), p.selectTime(shop, 22, 0))
        assertNull(p.selectTime(shop, 12, 14))
        assertNull(p.selectTime(shop, 22, 1))
        assertNull(p.selectTime(shop, 24, 0))
        assertNull(p.selectTime(shop, 12, 60))
    }

    @Test fun earliestTime_roundsUpToFiveMinutes() {
        assertEquals(LocalTime.of(12, 15), policy("09:00").earliestTime(shop))
        assertEquals(LocalTime.of(12, 20), policy("09:03").earliestTime(shop))
        assertEquals(LocalTime.of(22, 0), policy("18:45").earliestTime(shop))
        assertNull(policy("18:46").earliestTime(shop))
    }

    @Test fun asap_requiresPreparationBeforeClosing() {
        assertTrue(policy("18:45").isValid(shop, null))
        assertFalse(policy("18:46").isValid(shop, null))
        assertFalse(policy("20:50").isValid(shop, null))
    }

    @Test fun staleTime_isRejectedWithoutChangingIt() {
        var time = at("09:00")
        val p = PickupPolicy(now = { time }, hours = { PickupHours(zone, LocalTime.of(22, 0)) })
        val selected = p.selectTime(shop, 12, 20)!!
        time = at("09:10")
        assertFalse(p.isValid(shop, selected))
        assertEquals(at("09:20"), selected)
    }

    @Test fun previousAndNextDay_areRejected() {
        val p = policy("09:00")
        assertFalse(p.isValid(shop, at("09:30") - 86_400_000L))
        assertFalse(p.isValid(shop, at("09:30") + 86_400_000L))
        assertNull(policy("20:50").earliestTime(shop))
    }

    @Test fun knownOpening_affectsAsapAndScheduledPreparation() {
        val p = policy("05:00", LocalTime.of(9, 0)) // 08:00 Moscow
        assertFalse(p.isValid(shop, null))
        assertNull(p.selectTime(shop, 9, 0))
        assertEquals(at("06:15"), p.selectTime(shop, 9, 15))
        assertEquals(LocalTime.of(9, 15), p.earliestTime(shop))
        assertTrue(policy("06:00", LocalTime.of(9, 0)).isValid(shop, null))
    }

    @Test fun phoneTimezone_doesNotChangeShopTime() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(at("10:00"), policy("09:00").selectTime(shop, 13, 0))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun eachShopUsesItsOwnSchedule() {
        val p = PickupPolicy(now = { at("18:00") }, hours = { selected ->
            PickupHours(zone, LocalTime.of(if (selected == Shop.MOSKOVSKAYA) 22 else 21, 0))
        })
        assertTrue(p.isValid(Shop.MOSKOVSKAYA, null))
        assertFalse(p.isValid(Shop.STROITELEY, null))
    }
}
