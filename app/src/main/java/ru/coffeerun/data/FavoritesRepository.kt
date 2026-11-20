package ru.coffeerun.data

import kotlinx.coroutines.flow.StateFlow
import ru.coffeerun.data.session.AuthSession

/** Подтверждённые сервером ID. Последовательностью обращений управляет FavoritesCoordinator. */
interface FavoritesRepository {
    val hasSnapshot: Boolean get() = false
    val confirmedIds: StateFlow<Set<String>>
    suspend fun refresh(session: AuthSession)
    suspend fun setFavorite(productId: String, selected: Boolean, session: AuthSession)
    /** Сбрасывает данные для указанного актуального входа, включая гостевой. */
    fun clear(session: AuthSession)
}
