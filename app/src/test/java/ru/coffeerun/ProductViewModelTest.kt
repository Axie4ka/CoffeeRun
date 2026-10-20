package ru.coffeerun

import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.LocalMenuRepository
import ru.coffeerun.data.MenuRepository
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import ru.coffeerun.ui.screens.ProductUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.ui.screens.MenuViewModel
import ru.coffeerun.ui.screens.ProductForm
import ru.coffeerun.ui.screens.ProductViewModel

/** Форма напитка: правила выбора, цена и строка, которая уходит в корзину. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val cart = InMemoryCartRepository()
    private var savedState = SavedStateHandle()
    private fun vm(product: Product) = vm(product.id)
    private fun vm(id: String) = ProductViewModel(id, LocalMenuRepository(), cart, savedState)

    /** uiState живёт только при подписчике (WhileSubscribed), поэтому держим коллектор. */
    private fun TestScope.keepActive(vm: ProductViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun choices_changePriceAndSurviveViewModelRecreation() = runTest {
        val vm = vm(LocalMenu.LATTE)
        keepActive(vm)
        vm.selectSize(DrinkSize.LARGE)
        vm.selectMilk(MilkType.OAT)
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(400, (vm.uiState.value as ProductUiState.Success).unitPrice)
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(360, (vm.uiState.value as ProductUiState.Success).unitPrice)
        // Новая ViewModel с тем же SavedStateHandle — как после смерти процесса
        assertEquals(DrinkSize.LARGE, vm(LocalMenu.LATTE).form.value.size)
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
            override suspend fun getMenu(): List<Product> = LocalMenu.products
            override suspend fun getProduct(id: String): Product? = gate.await()
        }
        val vm = ProductViewModel(LocalMenu.LATTE.id, slow, cart, savedState)
        keepActive(vm)
        assertEquals(ProductUiState.Loading, vm.uiState.value)
        vm.selectSize(DrinkSize.LARGE)
        assertEquals(ProductUiState.Loading, vm.uiState.value)
        gate.complete(LocalMenu.LATTE)
        assertEquals(DrinkSize.LARGE, (vm.uiState.value as ProductUiState.Success).form.size)
    }

    @Test
    fun success_hasUnitPrice() = runTest {
        savedState = SavedStateHandle(
            mapOf("form" to ProductForm(size = DrinkSize.LARGE, milk = MilkType.OAT, extras = setOf(Extra.VANILLA)))
        )
        val vm = vm(LocalMenu.LATTE)
        keepActive(vm)
        assertEquals(400, (vm.uiState.value as ProductUiState.Success).unitPrice)
    }

    @Test
    fun quantity_staysWithinLimits() {
        val vm = vm(LocalMenu.LATTE)
        vm.changeQuantity(-1)
        assertEquals(MIN_QUANTITY, vm.form.value.quantity)
        repeat(MAX_QUANTITY + 5) { vm.changeQuantity(+1) }
        assertEquals(MAX_QUANTITY, vm.form.value.quantity)
    }

    @Test
    fun tooLongNote_isRejectedWhole() {
        val vm = vm(LocalMenu.LATTE)
        vm.setNote("без пенки")
        vm.setNote("x".repeat(MAX_NOTE_LENGTH + 1))
        assertEquals("без пенки", vm.form.value.note)
    }

    @Test
    fun addToCart_buildsLineThatMergesWithQuickAdd() = runTest {
        // Эспрессо без выбора размера: форма с размером M всё равно даёт те же опции, что «+»
        val vm = vm(LocalMenu.ESPRESSO)
        vm.setNote("  ")
        vm.changeQuantity(+1)
        vm.addToCart()
        MenuViewModel(LocalMenuRepository(), cart, InMemoryShopRepository()).quickAdd(LocalMenu.ESPRESSO)
        val lines = cart.lines.value
        assertEquals(1, lines.size)
        assertEquals(3, lines[0].quantity)
        assertNull(lines[0].options?.milk)
        assertEquals("", lines[0].options?.note)
    }

    @Test
    fun form_defaults() {
        assertEquals(ProductForm(), vm(LocalMenu.CAPPUCCINO).form.value)
    }
}
