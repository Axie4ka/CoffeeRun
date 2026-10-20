package ru.coffeerun.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.coffeerun.R
import ru.coffeerun.model.CartLine

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
        o.size?.let { add(stringResource(it.labelRes).substringBefore(' ')) }
        o.milk?.let { add(stringResource(it.labelRes)) }
        if (o.isIced) add(stringResource(R.string.temperature_iced))
        o.extras.forEach { add(stringResource(it.labelRes)) }
        if (o.note.isNotEmpty()) add("«${o.note}»")
    }
    // Ничего не выбрано (например, эспрессо без добавок) — показываем описание
    return if (parts.isEmpty()) stringResource(product.descriptionRes) else parts.joinToString(" · ")
}
