package ru.coffeerun.ui.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleReloadTest {
    @Test
    fun reloadsOnlyWhenLanguageChanges() {
        var reloads = 0
        val watch = ReloadOnLocaleChange { reloads++ }
        watch.onLocale(Locale.forLanguageTag("ru"))
        assertEquals("первую загрузку ViewModel делает сама", 0, reloads)
        watch.onLocale(Locale.forLanguageTag("ru"))
        assertEquals("вернулись на экран с тем же языком — в сеть не идём", 0, reloads)
        watch.onLocale(Locale.ENGLISH)
        assertEquals(1, reloads)
    }
}
