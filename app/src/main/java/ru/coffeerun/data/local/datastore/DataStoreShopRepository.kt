package ru.coffeerun.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.data.ShopRepository
import ru.coffeerun.model.Shop

class DataStoreShopRepository(private val data: DataStore<Preferences>, scope: CoroutineScope) : ShopRepository {
    override val selected = data.data.map(::decode)
        .retryWhen { error, _ ->
            if (error !is IOException) false else {
                delay(1000)
                true
            }
        }.stateIn(scope, SharingStarted.Eagerly, Shop.MOSKOVSKAYA)

    override suspend fun current() = decode(data.data.first())

    override suspend fun saveSelection(shop: Shop) {
        data.edit { it[SELECTED] = shop.name }
    }

    private fun decode(preferences: Preferences): Shop =
        Shop.entries.firstOrNull { it.name == preferences[SELECTED] } ?: Shop.MOSKOVSKAYA

    private companion object {
        val SELECTED = stringPreferencesKey("selected_shop")
    }
}
