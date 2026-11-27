package ru.coffeerun.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.coffeerun.R
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.domain.promotion.priceCart
import ru.coffeerun.domain.promotion.pricingTimes
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.Recipe
import ru.coffeerun.ui.util.ReloadOnLocaleChange

data class RecipeList(val session: AuthSession? = null, val recipes: List<Recipe> = emptyList(), val loading: Boolean = true)

data class RecipeMenu(val snapshot: MenuSnapshot, val nowMillis: Long) {
    fun portionPrice(recipe: Recipe): Long? {
        val portion = recipe.toCartLine(snapshot) ?: return null
        return priceCart(listOf(portion), snapshot, nowMillis).single().totalAmount
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModel(
    private val repository: RecipeRepository?,
    private val sessions: SessionProvider,
    private val menu: MenuRepository,
    private val cart: CartRepository
) : ViewModel() {
    private val retryVersion = MutableStateFlow(0)
    private val readRetry = MutableStateFlow(0)
    val readFailed = MutableStateFlow(false)
    private val sessionsWithRetry = combine(sessions.sessions, retryVersion) { session, _ -> session }
    val recipeMenu = MutableStateFlow<RecipeMenu?>(null)
    val refreshFailed = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<Int?>(null)
    val list = combine(sessions.sessions, readRetry) { session, _ -> session }.flatMapLatest { session ->
        readFailed.value = false
        if (!session.isLoggedIn || repository == null) flowOf(RecipeList(loading = false))
        else repository.observe(session)
            .map { RecipeList(session, it, loading = false) }
            .onStart { emit(RecipeList(session)) }
            .catch { error ->
                if (error !is IOException) throw error
                readFailed.value = true
                emit(RecipeList(session, loading = false))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), RecipeList())
    private var menuJob: Job? = null
    private var session: AuthSession? = null
    private val languageWatch = ReloadOnLocaleChange { loadMenu() }

    init {
        viewModelScope.launch {
            sessionsWithRetry.collectLatest { current ->
                if (session != current) message.value = null
                session = current
                refresh(current)
            }
        }
        loadMenu()
    }

    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    fun retry() {
        if (refreshing.value || repository == null || session?.isLoggedIn != true) return
        refreshing.value = true
        if (readFailed.value) readRetry.update { it + 1 }
        retryVersion.update { it + 1 }
        if (recipeMenu.value == null) loadMenu(refresh = true)
    }

    private suspend fun refresh(current: AuthSession) {
        refreshFailed.value = false
        if (!current.isLoggedIn || repository == null) {
            refreshing.value = false
            return
        }
        refreshing.value = true
        try {
            repository.refresh(current)
        } catch (_: IOException) {
            refreshFailed.value = true
        } finally {
            refreshing.value = false
        }
    }

    private fun loadMenu(refresh: Boolean = false) {
        menuJob?.cancel()
        menuJob = viewModelScope.launch {
            if (refresh) try { menu.refresh() } catch (_: IOException) { }
            try {
                menu.observeSnapshot().collectLatest { snapshot ->
                    snapshot.pricingTimes().collect { recipeMenu.value = RecipeMenu(snapshot, it) }
                }
            } catch (_: IOException) {
                recipeMenu.value = null
            }
        }
    }

    fun add(recipe: Recipe, session: AuthSession) = act(session, R.string.recipe_added) { source ->
        val current = source.find(recipe.id, session) ?: return@act R.string.recipe_unavailable
        val line = current.toCartLine(menu.observeSnapshot().first()) ?: return@act R.string.recipe_unavailable
        if (cart.addWithinLimit(listOf(line), session).single()) null else R.string.recipe_cart_limit
    }

    fun rename(recipe: Recipe, name: String, session: AuthSession) =
        act(session, R.string.recipe_saved) { source ->
            if (source.rename(recipe.id, name, session) == null) R.string.recipe_already_deleted else null
        }

    fun remove(recipe: Recipe, session: AuthSession) = act(session, R.string.recipe_deleted) { source ->
        source.remove(recipe.id, session)
        null
    }

    private fun act(expected: AuthSession, success: Int, action: suspend (RecipeRepository) -> Int?) {
        val source = repository ?: return
        if (busy.value || session != expected) return
        busy.value = true
        viewModelScope.launch {
            try {
                val error = action(source)
                sessions.withSession(expected) { message.value = error ?: success }
            } catch (_: IOException) {
                sessions.withSession(expected) { message.value = R.string.recipe_action_error }
            } finally {
                busy.value = false
            }
        }
    }

    fun onMessageShown() { message.value = null }

    companion object {
        val Factory = viewModelFactory {
            initializer { RecipesViewModel(container.recipes, container.auth, container.menu, container.cart) }
        }
    }
}
