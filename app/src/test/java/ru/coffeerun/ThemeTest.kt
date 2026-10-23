package ru.coffeerun

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.coffeerun.ui.theme.darkScheme
import ru.coffeerun.ui.theme.lightScheme
import ru.coffeerun.ui.theme.onTertiaryFixed
import ru.coffeerun.ui.theme.tertiaryFixedDim

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
