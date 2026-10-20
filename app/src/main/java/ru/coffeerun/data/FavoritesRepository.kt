package ru.coffeerun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Избранное — id товаров. Наружу только чтение через [ids], менять можно лишь [toggle]. */
interface FavoritesRepository {
    val ids: StateFlow<Set<String>>

    /** Добавляет товар в избранное, а если он уже там — убирает. */
    fun toggle(productId: String)
}

// shortcut: избранное теряется при смерти процесса, в лабе 6 — DataStore
/** Избранное в памяти. */
class InMemoryFavoritesRepository : FavoritesRepository {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override fun toggle(productId: String) = _ids.update { if (productId in it) it - productId else it + productId }
}
