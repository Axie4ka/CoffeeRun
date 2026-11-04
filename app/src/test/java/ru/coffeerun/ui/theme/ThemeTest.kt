package ru.coffeerun.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Бейдж корзины берёт фиксированные роли — в обеих темах это фирменная карамель, а не цвета Material по умолчанию. */
class ThemeTest {
    @Test
    fun fixedRoles_comeFromBrandPalette() {
        for (scheme in listOf(lightScheme, darkScheme)) {
            assertEquals(tertiaryFixedDim, scheme.tertiaryFixedDim)
            assertEquals(onTertiaryFixed, scheme.onTertiaryFixed)
        }
    }
}
