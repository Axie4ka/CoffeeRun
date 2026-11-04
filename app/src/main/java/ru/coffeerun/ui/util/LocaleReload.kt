package ru.coffeerun.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * Названия и описания позиций приходят с сервера на языке приложения. ViewModel переживает смену языка
 * вместе со старыми названиями — этот помощник просит загрузить их заново, когда язык сменился.
 * Первый вызов только запоминает язык: первую загрузку ViewModel делает сама.
 */
class ReloadOnLocaleChange(private val reload: () -> Unit) {
    private var shown: Locale? = null

    fun onLocale(locale: Locale) {
        if (shown != null && shown != locale) reload()
        shown = locale
    }
}

/** Сообщает текущий язык при показе экрана и после каждой его смены. */
@Composable
fun OnLocale(onLocale: (Locale) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { onLocale(locale) }
}
