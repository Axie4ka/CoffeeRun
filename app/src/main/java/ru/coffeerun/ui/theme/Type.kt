package ru.coffeerun.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.coffeerun.R

// Nunito — для интерфейса, Yeseva One — для бренда: логотип, заголовки экранов, цены.
val bodyFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold)
)

// У Yeseva One одно начертание: жирность в стилях не задаём, иначе Android нарисует искусственный bold
val brandFontFamily = FontFamily(
    Font(R.font.yeseva_one_regular, FontWeight.Normal)
)

// Стандартные значения Material 3, меняем только шрифты
private val baseline = Typography()

val AppTypography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = brandFontFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = brandFontFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = brandFontFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = brandFontFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = brandFontFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = brandFontFamily),
    titleLarge = baseline.titleLarge.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = baseline.titleMedium.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = baseline.titleSmall.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily, fontWeight = FontWeight.SemiBold),
)

// Цены — отдельные стили: в Typography M3 своих полей не добавить
private val priceSmallStyle = TextStyle(fontFamily = brandFontFamily, fontSize = 18.sp, lineHeight = 24.sp)
private val priceLargeStyle = TextStyle(fontFamily = brandFontFamily, fontSize = 24.sp, lineHeight = 32.sp)

/** Цена в карточке меню, строке корзины и карточке заказа. */
val Typography.priceSmall: TextStyle get() = priceSmallStyle

/** Цена на экране позиции и «Итого». */
val Typography.priceLarge: TextStyle get() = priceLargeStyle
