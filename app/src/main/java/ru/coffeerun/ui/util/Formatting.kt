package ru.coffeerun.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «9:41» — время в локали устройства. */
@Composable
fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = format(millis, "H:mm", zone)

/** «20 октября, 9:41» — дата и время в локали устройства. */
@Composable
fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = format(millis, "d MMMM, H:mm", zone)

@Composable
private fun format(millis: Long, pattern: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.ofEpochMilli(millis).atZone(zone))
}
