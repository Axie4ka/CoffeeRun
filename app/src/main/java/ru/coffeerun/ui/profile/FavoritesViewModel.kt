package ru.coffeerun.ui.profile

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
import ru.coffeerun.data.FavoritesRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.model.Product

/** Состояние экрана «Избранное». */
sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState
    data class Success(val products: List<Product>) : FavoritesUiState

    /** Каталог не загрузился: нет сети или сервер ответил ошибкой. Выход — [FavoritesViewModel.retry]. */
    data object Error : FavoritesUiState
}

/** Избранное: товары каталога, чьи id в избранном, в порядке каталога; неизвестные id пропускаются. */
class FavoritesViewModel(private val menu: MenuRepository, favorites: FavoritesRepository) : ViewModel() {
    /** null — грузится, failure — ошибка загрузки. */
    private val catalog = MutableStateFlow<Result<List<Product>>?>(null)
    private var loadJob: Job? = null

    val uiState: StateFlow<FavoritesUiState> = combine(catalog, favorites.ids) { catalog, ids ->
        when {
            catalog == null -> FavoritesUiState.Loading
            catalog.isFailure -> FavoritesUiState.Error
            else -> FavoritesUiState.Success(catalog.getOrThrow().filter { it.id in ids })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    init {
        retry()
    }

    /** Первая загрузка и «Повторить» после ошибки. Ловим только [IOException]: остальное — баг. */
    fun retry() {
        loadJob?.cancel()
        catalog.value = null
        loadJob = viewModelScope.launch {
            catalog.value = try {
                Result.success(menu.getMenu())
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { FavoritesViewModel(container.menu, container.favorites) } }
    }
}
