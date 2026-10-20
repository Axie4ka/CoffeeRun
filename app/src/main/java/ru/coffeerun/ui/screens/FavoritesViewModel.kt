package ru.coffeerun.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
}

/** Избранное: товары каталога, чьи id в избранном, в порядке каталога; неизвестные id пропускаются. */
class FavoritesViewModel(menu: MenuRepository, favorites: FavoritesRepository) : ViewModel() {
    private val catalog = MutableStateFlow<List<Product>?>(null)

    val uiState: StateFlow<FavoritesUiState> = combine(catalog, favorites.ids) { catalog, ids ->
        if (catalog == null) FavoritesUiState.Loading
        else FavoritesUiState.Success(catalog.filter { it.id in ids })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    init {
        viewModelScope.launch { catalog.value = menu.getMenu() }
    }

    companion object {
        val Factory = viewModelFactory { initializer { FavoritesViewModel(container.menu, container.favorites) } }
    }
}
