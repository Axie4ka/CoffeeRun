package ru.coffeerun.ui.auth

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Цифр после +7 в российском номере. */
const val PHONE_DIGITS = 10

/** Пароль для регистрации — не короче, как в контракте API. */
const val MIN_PASSWORD_LENGTH = 6

/**
 * Из того, что ввели или вставили, — только цифры номера после +7, не больше [PHONE_DIGITS].
 * Код страны отбрасывается: у вставленного целиком номера («+7 812 …», «8 999 …») и у набора по привычке.
 * Набранная первой 7 — всегда код страны. Первая 8 ждёт следующей цифры: «89…» — привычка набирать
 * мобильный с 8, а «81…», «84…» — городской код (Петербург — 812), и восьмёрка остаётся.
 * [previous] — цифры до правки: 11 цифр при уже набранных 10 — лишняя цифра в конце, а не код страны.
 * shortcut: «8 812 …» по одной цифре не распознаётся (вставка работает); понадобится — разбирать по списку кодов.
 */
fun phoneDigits(input: String, previous: String = ""): String {
    val digits = input.filter(Char::isDigit)
    val pastedWithCountry = digits.length == PHONE_DIGITS + 1 && digits[0] in "78" && previous.length != PHONE_DIGITS
    val typedCountry = previous.length <= 1 && (digits.startsWith('7') || digits.startsWith("89"))
    val withoutCountry = if (pastedWithCountry || typedCountry) digits.drop(1) else digits
    return withoutCountry.take(PHONE_DIGITS)
}

/** Российский номер: 10 цифр после +7. Коды начинаются с 3, 4, 8 (городские) или 9 (мобильные). */
fun isValidPhone(digits: String): Boolean = digits.length == PHONE_DIGITS && digits[0] in "3489"

/** Номер в формате контракта: +7 и 10 цифр. */
fun fullPhone(digits: String): String = "+7$digits"

/** «+79990000000» → «+7 999 000-00-00» для показа. Номер не в формате контракта показывается как есть. */
fun displayPhone(phone: String): String =
    if (phone.startsWith("+7") && phone.length == 2 + PHONE_DIGITS) "+7 " + formatPhone(phone.drop(2)) else phone

/** «9990000000» → «999 000-00-00». Пробел после 3-й цифры, дефисы после 6-й и 8-й. */
fun formatPhone(digits: String): String = buildString {
    digits.forEachIndexed { i, c ->
        when (i) {
            3 -> append(' ')
            6, 8 -> append('-')
        }
        append(c)
    }
}

/**
 * Показывает номер группами, а в поле хранятся только цифры. Курсор пересчитывается через
 * разделители, чтобы стирание и ввод в середине номера работали как ожидается.
 */
object PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        return TransformedText(AnnotatedString(formatPhone(digits)), object : OffsetMapping {
            // До скольки разделителей стоит позиция: после 3-й, 6-й и 8-й цифры
            override fun originalToTransformed(offset: Int): Int = offset + listOf(3, 6, 8).count { offset > it }
            override fun transformedToOriginal(offset: Int): Int =
                (offset - listOf(3, 7, 10).count { offset > it }).coerceIn(0, digits.length)
        })
    }
}
