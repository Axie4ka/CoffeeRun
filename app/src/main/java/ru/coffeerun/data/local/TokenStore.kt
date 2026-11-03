package ru.coffeerun.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Имя файла DataStore с токеном; он же исключён из резервной копии (backup_rules.xml, data_extraction_rules.xml). */
const val AUTH_DATASTORE = "auth"

/**
 * Токен входа. Хранится в DataStore в личной папке приложения и переживает перезапуск —
 * поэтому входить заново не нужно. Пароль не хранится никогда.
 * [dataStore] передаётся снаружи: в приложении — файл [AUTH_DATASTORE], в тестах — временный.
 */
class TokenStore(private val dataStore: DataStore<Preferences>) {
    /** null — пользователь не вошёл. Новое значение приходит сразу после [save] или [clear]. */
    val token: Flow<String?> = dataStore.data.map { it[TOKEN] }

    suspend fun save(token: String) {
        dataStore.edit { it[TOKEN] = token }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(TOKEN) }
    }

    /**
     * Стирает токен, только если сохранён всё ещё [token]. Сервер отклонил старый токен,
     * а пользователь за это время мог войти заново — новый токен трогать нельзя.
     */
    suspend fun clearIfStill(token: String) {
        dataStore.edit { if (it[TOKEN] == token) it.remove(TOKEN) }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
    }
}
