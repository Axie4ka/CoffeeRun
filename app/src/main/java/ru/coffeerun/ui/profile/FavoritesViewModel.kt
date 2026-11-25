package ru.coffeerun.ui.profile

import ru.coffeerun.ui.util.ReloadOnLocaleChange
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.Product

/** Состояние экрана «Избранное». */
sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(
        val products: List<Product>,
        val isCached: Boolean = false,
        val missingIds: Set<String> = emptySet()
    ) : FavoritesUiState

    /** Каталог не загрузился: нет сети или сервер ответил ошибкой. Выход — [FavoritesViewModel.retry]. */
    data object CatalogError : FavoritesUiState
    data object Error : FavoritesUiState
}

/**
 * Избранные товары сохраняют порядок каталога. Свежий каталог позволяет удалить отсутствующие позиции.
 * При открытии экрана избранное заново берётся с сервера — вдруг его меняли на другом устройстве.
 */
class FavoritesViewModel(private val menu: MenuRepository, private val favorites: Favorites) : ViewModel() {
    private data class Snapshot(val idsKnown: Boolean, val idsCached: Boolean, val menu: MenuSnapshot?, val loadingMenu: Boolean = false)
    private val snapshot = MutableStateFlow<Snapshot?>(null)
    private var loadJob: Job? = null

    val uiState: StateFlow<FavoritesUiState> = combine(snapshot, favorites.ids) { state, ids ->
        when {
            state == null -> FavoritesUiState.Loading
            !state.idsKnown -> FavoritesUiState.Error
            ids.isEmpty() -> FavoritesUiState.Success(emptyList(), state.idsCached)
            state.loadingMenu -> FavoritesUiState.Loading
            state.menu == null -> FavoritesUiState.CatalogError
            else -> {
                val products = state.menu.products.filter { it.id in ids }
                val missing = ids - state.menu.catalogProductIds
                if (state.menu.isCached && products.isEmpty()) FavoritesUiState.CatalogError
                else FavoritesUiState.Success(
                    products, state.idsCached || state.menu.isCached,
                    if (state.menu.isCached) emptySet() else missing
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    init {
        retry()
    }

    private val languageWatch = ReloadOnLocaleChange {
        snapshot.value = null
        retry()
    }

    /** Экран сообщает язык; сменился — данные с сервера загружаются заново, уже на новом языке. */
    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val refreshed = try {
                favorites.refresh()
                true
            } catch (_: IOException) { false }
            val idsKnown = refreshed || favorites.hasSnapshot
            if (!idsKnown) {
                snapshot.value = Snapshot(false, false, null)
                return@launch
            }
            if (favorites.ids.value.isEmpty()) {
                snapshot.value = Snapshot(true, !refreshed, null, loadingMenu = true)
                favorites.ids.first { it.isNotEmpty() }
            }
            val catalog = try {
                menu.refresh()
            } catch (_: IOException) {
                try { menu.observeSnapshot().first().copy(isCached = true) } catch (_: IOException) { null }
            }
            snapshot.value = Snapshot(idsKnown, !refreshed, catalog)
        }
    }

    fun removeMissing(id: String, onFailure: () -> Unit) {
        viewModelScope.launch {
            val confirmedMissing = (uiState.value as? FavoritesUiState.Success)?.missingIds.orEmpty()
            if (id in confirmedMissing && id in favorites.ids.value && !favorites.toggle(id)) onFailure()
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { FavoritesViewModel(container.menu, container.favorites) } }
    }
}
