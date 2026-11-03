package ru.coffeerun.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Избранное в памяти — для UI-тестов и превью. */
class InMemoryFavoritesRepository : FavoritesRepository {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun toggle(productId: String): Boolean {
        _ids.update { if (productId in it) it - productId else it + productId }
        return true
    }
}
