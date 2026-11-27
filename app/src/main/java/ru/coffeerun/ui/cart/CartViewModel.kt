package ru.coffeerun.ui.cart

import ru.coffeerun.data.MenuRepository
import kotlinx.coroutines.launch
import java.io.IOException
import ru.coffeerun.ui.util.ReloadOnLocaleChange
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.coffeerun.container
import ru.coffeerun.data.CartRepository
import ru.coffeerun.data.CartRead
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.itemCount
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.domain.promotion.priceCart
import ru.coffeerun.domain.promotion.promotionProductIds
import ru.coffeerun.domain.promotion.pricingTimes
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.PromotionType

/** Состояние корзины для экрана и бейджа нижней панели: позиции, число штук и итог. */
data class CartUiState(
    val lines: List<CartLine>, val count: Int, val total: Int,
    val isLoading: Boolean = false, val readFailed: Boolean = false,
    val discount: Long = 0, val recommendation: Product? = null, val completesPromotion: Boolean = false
)

private fun toCartUiState(readState: CartRead, menu: MenuSnapshot? = null, now: Long = System.currentTimeMillis()): CartUiState {
    val lines = priceCart((readState as? CartRead.Ready)?.lines.orEmpty(), menu, now)
    val total = lines.sumOf { it.totalAmount }
    val pastryCount = lines.filter { it.product.category == ProductCategory.PASTRY }.sumOf { it.quantity }
    val pastryPromotion = menu?.activePromotions(now)?.firstOrNull { it.type == PromotionType.PASTRY_2_PLUS_1 }
    val eligible = pastryPromotion?.let { menu.promotionProductIds(it.category) }.orEmpty()
    val completesPromotion = lines.filter { it.product.id in eligible }.sumOf { it.quantity } % 3 == 2
    val recommendation = if (completesPromotion || (pastryCount == 0 && lines.any { it.product.category != ProductCategory.PASTRY })) {
        menu?.products?.filter { product ->
            product.category == ProductCategory.PASTRY && (!completesPromotion || product.id in eligible) &&
                product.recipePrice(null, menu.prices) != null &&
                lines.none { it.product.id == product.id && it.quantity >= ru.coffeerun.model.MAX_QUANTITY }
        }?.minByOrNull { it.price }
    } else null
    return CartUiState(lines, lines.itemCount, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        isLoading = readState == CartRead.Loading, readFailed = readState == CartRead.Failed,
        discount = lines.sumOf { it.discount.toLong() },
        recommendation = recommendation, completesPromotion = completesPromotion)
}

/** Корзина: состояние и действия над позициями. Нижняя панель берёт отсюда же счётчик. */
class CartViewModel(
    private val cart: CartRepository,
    private val menu: MenuRepository,
    private val sessions: SessionProvider? = null
) : ViewModel() {
    class RemovedLine(val line: CartLine, val session: AuthSession)

    private val _removedLine = MutableStateFlow<RemovedLine?>(null)
    val removedLine = _removedLine.asStateFlow()
    private val _restoreLimit = MutableStateFlow(false)
    val restoreLimit = _restoreLimit.asStateFlow()
    private val menuSnapshot = MutableStateFlow<Pair<MenuSnapshot, Long>?>(null)
    private var menuJob: kotlinx.coroutines.Job? = null

    init {
        if (sessions != null) viewModelScope.launch {
            sessions.sessions.collect { session ->
                _removedLine.update { it?.takeIf { removal -> removal.session == session } }
            }
        }
    }
    // Начальное значение из текущих позиций — чтобы бейдж не мигал нулём
    val uiState: StateFlow<CartUiState> = cart.readState
        .combine(menuSnapshot) { cart, menu -> toCartUiState(cart, menu?.first, menu?.second ?: System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            toCartUiState((cart.readState as? StateFlow<CartRead>)?.value ?: CartRead.Ready(cart.lines.value)))

    fun onShown() {
        menuJob?.cancel()
        menuJob = viewModelScope.launch {
            try {
                menu.observeSnapshot().collectLatest { snapshot ->
                    snapshot.pricingTimes().collect { time -> menuSnapshot.value = snapshot to time }
                }
            } catch (_: IOException) { }
        }
    }

    fun addRecommendation(onLimit: () -> Unit) {
        val product = uiState.value.recommendation ?: return
        val prices = menuSnapshot.value?.first?.prices ?: return
        val price = product.recipePrice(null, prices) ?: return
        viewModelScope.launch {
            updateCart { if (!cart.addLine(CartLine(product, price))) onLimit() }
        }
    }

    fun retryRead() = cart.retryRead()

    fun changeQuantity(id: Int, delta: Int) { viewModelScope.launch { updateCart { cart.changeLineQuantity(id, delta) } } }
    fun remove(id: Int) {
        viewModelScope.launch {
            updateCart {
                val session = sessions?.currentSession()
                val removed = cart.removeLine(id, session)
                if (removed != null && session != null) {
                    sessions.withSession(session) { _removedLine.value = RemovedLine(removed, session) }
                }
            }
        }
    }

    fun finishRemoval(removal: RemovedLine, restore: Boolean) {
        if (!_removedLine.compareAndSet(removal, null) || !restore) return
        viewModelScope.launch {
            updateCart {
                if (sessions?.currentSession() != removal.session) return@updateCart
                val restored = cart.addWithinLimit(listOf(removal.line.copy(id = 0)), removal.session).single()
                if (!restored) _restoreLimit.value = true
            }
        }
    }

    fun onRestoreLimitShown() { _restoreLimit.value = false }

    private suspend fun updateCart(action: suspend () -> Unit) {
        try { action() } catch (_: IOException) { _storageFailed.value = true }
    }

    private val _storageFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val storageFailed: StateFlow<Boolean> = _storageFailed
    fun onStorageErrorShown() { _storageFailed.value = false }

    // В строках корзины — товары на языке, на котором их добавили; сменился язык — берём из меню заново
    private val languageWatch = ReloadOnLocaleChange {
        onShown()
        viewModelScope.launch {
            try { cart.updateProducts(menu.getMenu()) } catch (e: IOException) { } // нет сети — останутся старые названия
        }
    }

    fun onLocale(locale: Locale) = languageWatch.onLocale(locale)

    companion object {
        val Factory = viewModelFactory { initializer { CartViewModel(container.cart, container.menu, container.auth) } }
    }
}
