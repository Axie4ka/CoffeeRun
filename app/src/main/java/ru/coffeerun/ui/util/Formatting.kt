package ru.coffeerun.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «9:41» — время в локали устройства. */
@Composable
fun formatTime(millis: Long): String = format(millis, "H:mm")

/** «20 октября, 9:41» — дата и время в локали устройства. */
@Composable
fun formatDateTime(millis: Long): String = format(millis, "d MMMM, H:mm")

@Composable
private fun format(millis: Long, pattern: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
