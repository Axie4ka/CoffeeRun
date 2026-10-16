package ru.coffeerun.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.model.CartLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «Капучино L × 1» — короткая строка позиции для заказа. */
@Composable
fun CartLine.summaryText(): String {
    val name = stringResource(product.nameRes)
    val size = options?.size?.let { stringResource(it.labelRes).substringBefore(' ') }
    return if (size != null) {
        stringResource(R.string.line_summary_sized, name, size, quantity)
    } else {
        stringResource(R.string.line_summary, name, quantity)
    }
}

/** «L · Овсяное · Ваниль» для напитка с опциями, иначе описание позиции. */
@Composable
fun CartLine.optionsText(): String {
    val o = options ?: return stringResource(product.descriptionRes)
    val parts = buildList {
        // Метка размера выглядит как «M · 350 мл» — в корзине нужна только буква
        add(stringResource(o.size.labelRes).substringBefore(' '))
        add(stringResource(o.milk.labelRes))
        if (o.isIced) add(stringResource(R.string.temperature_iced))
        o.extras.forEach { add(stringResource(it.labelRes)) }
    }
    return parts.joinToString(" · ")
}

@Composable
fun formatTime(millis: Long): String = format(millis, "H:mm")

@Composable
fun formatDateTime(millis: Long): String = format(millis, "d MMMM, H:mm")

@Composable
private fun format(millis: Long, pattern: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(pattern, locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
