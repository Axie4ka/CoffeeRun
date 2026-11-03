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
 * Код страны отбрасывается: и у вставленного целиком номера («+7 999 …», «8 999 …»), и у набора
 * «по привычке с 8» — первая 8 или 7 в пустом поле не цифра номера, ведь +7 уже показан.
 * [previous] — цифры до правки: 11 цифр при уже набранных 10 — лишняя цифра в конце, а не код страны.
 */
fun phoneDigits(input: String, previous: String = ""): String {
    val digits = input.filter(Char::isDigit)
    val startsWithCountry = digits.isNotEmpty() && digits[0] in "78"
    val withoutCountry = when {
        startsWithCountry && digits.length == PHONE_DIGITS + 1 && previous.length != PHONE_DIGITS -> digits.drop(1)
        startsWithCountry && previous.isEmpty() -> digits.drop(1)
        else -> digits
    }
    return withoutCountry.take(PHONE_DIGITS)
}

/**
 * Мобильный номер: 10 цифр после +7, первая — 9.
 * shortcut: городские номера (+7 812 …) не принимаются — кофейне нужен мобильный; понадобятся — ослабить до длины.
 */
fun isValidPhone(digits: String): Boolean = digits.length == PHONE_DIGITS && digits[0] == '9'

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
