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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.coffeerun.container
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.MenuRepository
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
    data object Error : FavoritesUiState
}

/**
 * Избранное: товары каталога, чьи id в избранном, в порядке каталога; неизвестные id пропускаются.
 * При открытии экрана избранное заново берётся с сервера — вдруг его меняли на другом устройстве.
 */
class FavoritesViewModel(private val menu: MenuRepository, private val favorites: Favorites) : ViewModel() {
    /** null — грузится, failure — ошибка загрузки. */
    private val catalog = MutableStateFlow<Result<List<Product>>?>(null)
    private var loadJob: Job? = null
    private val cached = MutableStateFlow(false)

    val uiState: StateFlow<FavoritesUiState> = combine(catalog, favorites.ids, cached) { catalog, ids, cached ->
        when {
            catalog == null -> FavoritesUiState.Loading
            catalog.isFailure -> FavoritesUiState.Error
            else -> {
                val products = catalog.getOrThrow().filter { it.id in ids }
                FavoritesUiState.Success(products, cached, ids - products.map { it.id }.toSet())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    init {
        retry()
    }

    private val languageWatch = ReloadOnLocaleChange(::retry)

    /** Экран сообщает язык; сменился — данные с сервера загружаются заново, уже на новом языке. */
    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel()
        catalog.value = null
        cached.value = false
        loadJob = viewModelScope.launch {
            val products = try { menu.getMenu() } catch (_: IOException) { null }
            val refreshed = try {
                favorites.refresh()
                true
            } catch (_: IOException) { false }
            cached.value = !refreshed || products == null
            catalog.value = if ((refreshed && products != null) || favorites.hasSnapshot) Result.success(products.orEmpty())
                else Result.failure(IOException("Избранное недоступно"))
        }
    }

    fun removeMissing(id: String, onFailure: () -> Unit) {
        viewModelScope.launch {
            if (id in favorites.ids.value && !favorites.toggle(id)) onFailure()
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { FavoritesViewModel(container.menu, container.favorites) } }
    }
}
