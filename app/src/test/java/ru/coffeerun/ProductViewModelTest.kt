package ru.coffeerun

import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.MenuRepository
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import ru.coffeerun.ui.product.ProductUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.ui.menu.MenuViewModel
import ru.coffeerun.ui.product.ProductForm
import ru.coffeerun.ui.product.ProductViewModel

/** Форма напитка: правила выбора, цена и строка, которая уходит в корзину. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val cart = InMemoryCartRepository()
    private var savedState = SavedStateHandle()
    private fun vm(product: Product) = vm(product.id)
    private fun vm(id: String) = ProductViewModel(id, DemoMenuRepository(), cart, InMemoryFavoritesRepository(), savedState)

    /** uiState живёт только при подписчике (WhileSubscribed), поэтому держим коллектор. */
    private fun TestScope.keepActive(vm: ProductViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun choices_changePriceAndSurviveViewModelRecreation() = runTest {
        val vm = vm(DemoMenu.LATTE)
        keepActive(vm)
        vm.selectSize(DrinkSize.LARGE)
        vm.selectMilk(MilkType.OAT)
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(400, (vm.uiState.value as ProductUiState.Success).unitPrice)
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(360, (vm.uiState.value as ProductUiState.Success).unitPrice)
        // Новая ViewModel с тем же SavedStateHandle — как после смерти процесса
        assertEquals(DrinkSize.LARGE, vm(DemoMenu.LATTE).form.value.size)
    }

    @Test
    fun unknownId_isNotFound() = runTest {
        val vm = vm("unknown")
        keepActive(vm)
        assertEquals(ProductUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun form_survivesWhileProductLoads() = runTest {
        val gate = CompletableDeferred<Product?>()
        val slow = object : MenuRepository {
            override suspend fun getMenu(): List<Product> = DemoMenu.products
            override suspend fun getProduct(id: String): Product? = gate.await()
            override suspend fun getOptionPrices() = DemoMenu.prices
        }
        val vm = ProductViewModel(DemoMenu.LATTE.id, slow, cart, InMemoryFavoritesRepository(), savedState)
        keepActive(vm)
        assertEquals(ProductUiState.Loading, vm.uiState.value)
        vm.selectSize(DrinkSize.LARGE)
        assertEquals(ProductUiState.Loading, vm.uiState.value)
        gate.complete(DemoMenu.LATTE)
        assertEquals(DrinkSize.LARGE, (vm.uiState.value as ProductUiState.Success).form.size)
    }

    @Test
    fun success_hasUnitPrice() = runTest {
        savedState = SavedStateHandle(
            mapOf("form" to ProductForm(size = DrinkSize.LARGE, milk = MilkType.OAT, extras = setOf(Extra.VANILLA)))
        )
        val vm = vm(DemoMenu.LATTE)
        keepActive(vm)
        assertEquals(400, (vm.uiState.value as ProductUiState.Success).unitPrice)
    }

    @Test
    fun quantity_staysWithinLimits() {
        val vm = vm(DemoMenu.LATTE)
        vm.changeQuantity(-1)
        assertEquals(MIN_QUANTITY, vm.form.value.quantity)
        repeat(MAX_QUANTITY + 5) { vm.changeQuantity(+1) }
        assertEquals(MAX_QUANTITY, vm.form.value.quantity)
    }

    @Test
    fun tooLongNote_isRejectedWhole() {
        val vm = vm(DemoMenu.LATTE)
        vm.setNote("без пенки")
        vm.setNote("x".repeat(MAX_NOTE_LENGTH + 1))
        assertEquals("без пенки", vm.form.value.note)
    }

    @Test
    fun addToCart_buildsLineThatMergesWithQuickAdd() = runTest {
        // Эспрессо без выбора размера: форма с размером M всё равно даёт те же опции, что «+»
        val vm = vm(DemoMenu.ESPRESSO)
        vm.setNote("  ")
        vm.changeQuantity(+1)
        vm.addToCart()
        MenuViewModel(DemoMenuRepository(), cart, InMemoryShopRepository()).quickAdd(DemoMenu.ESPRESSO)
        val lines = cart.lines.value
        assertEquals(1, lines.size)
        assertEquals(3, lines[0].quantity)
        assertNull(lines[0].options?.milk)
        assertEquals("", lines[0].options?.note)
    }

    @Test
    fun goneOptions_replacedByAvailable_inScreenAndQuickAdd() = runTest {
        // В кофейне закончилось обычное молоко и убрали размер M — выбор по умолчанию недоступен
        val prices = DemoMenu.prices.copy(
            sizes = DemoMenu.prices.sizes.filter { it.size != DrinkSize.MEDIUM },
            milks = DemoMenu.prices.milks.map { if (it.milk == MilkType.REGULAR) it.copy(isAvailable = false) else it }
        )
        val repo = object : MenuRepository {
            override suspend fun getMenu(): List<Product> = DemoMenu.products
            override suspend fun getProduct(id: String): Product? = DemoMenu.byId(id)
            override suspend fun getOptionPrices() = prices
        }
        val vm = ProductViewModel(DemoMenu.LATTE.id, repo, cart, InMemoryFavoritesRepository(), savedState)
        keepActive(vm)
        val form = (vm.uiState.value as ProductUiState.Success).form
        assertEquals(DrinkSize.SMALL, form.size)
        assertEquals(MilkType.LACTOSE_FREE, form.milk)

        vm.addToCart()
        MenuViewModel(repo, cart, InMemoryShopRepository()).quickAdd(DemoMenu.LATTE)
        val lines = cart.lines.value
        assertEquals("экран и «+» дают одну строку", 1, lines.size)
        assertEquals(MilkType.LACTOSE_FREE, lines[0].options?.milk)
        assertEquals(240, lines[0].unitPrice)
    }

    @Test
    fun quickAddAndUntouchedForm_mergeIntoOneLine() {
        MenuViewModel(DemoMenuRepository(), cart, InMemoryShopRepository()).quickAdd(DemoMenu.CAPPUCCINO)
        vm(DemoMenu.CAPPUCCINO).addToCart()
        val lines = cart.lines.value
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].quantity)
        assertEquals(260, lines[0].unitPrice)
    }

    @Test
    fun success_hasCartCountForBadge() = runTest {
        val vm = vm(DemoMenu.LATTE)
        keepActive(vm)
        assertEquals(0, (vm.uiState.value as ProductUiState.Success).cartCount)
        vm.changeQuantity(+1)
        vm.addToCart()
        assertEquals(2, (vm.uiState.value as ProductUiState.Success).cartCount)
    }

    @Test
    fun form_defaults() {
        assertEquals(ProductForm(), vm(DemoMenu.CAPPUCCINO).form.value)
    }
}
