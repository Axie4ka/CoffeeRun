package ru.coffeerun

import ru.coffeerun.data.LocalMenu
import ru.coffeerun.data.LocalMenuRepository
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.CartRepository
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
class ProductViewModelTest {
    private val cart = CartRepository()
    private val savedState = SavedStateHandle()
    private fun vm(product: Product) = ProductViewModel(product, cart, savedState)

    @Test
    fun choices_changePriceAndSurviveViewModelRecreation() {
        val vm = vm(LocalMenu.LATTE)
        vm.selectSize(DrinkSize.LARGE)
        vm.selectMilk(MilkType.OAT)
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(400, vm.unitPrice(vm.form.value))
        vm.toggleExtra(Extra.VANILLA)
        assertEquals(360, vm.unitPrice(vm.form.value))
        // Новая ViewModel с тем же SavedStateHandle — как после смерти процесса
        assertEquals(DrinkSize.LARGE, vm(LocalMenu.LATTE).form.value.size)
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
    fun addToCart_buildsLineThatMergesWithQuickAdd() {
        // Эспрессо без выбора размера: форма с размером M всё равно даёт те же опции, что «+»
        val vm = vm(LocalMenu.ESPRESSO)
        vm.setNote("  ")
        vm.changeQuantity(+1)
        vm.addToCart()
        MenuViewModel(LocalMenuRepository(), cart).quickAdd(LocalMenu.ESPRESSO)
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
