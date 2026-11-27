package ru.coffeerun.ui.product

import ru.coffeerun.MainDispatcherRule
import ru.coffeerun.R
import ru.coffeerun.testCheckout
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.MenuRepository
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.coffeerun.data.InMemoryCartRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryShopRepository
import ru.coffeerun.data.RecipeRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.MAX_QUANTITY
import ru.coffeerun.model.DrinkSize
import ru.coffeerun.model.Extra
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.MenuSnapshot
import ru.coffeerun.model.Product
import ru.coffeerun.model.ProductCategory
import ru.coffeerun.model.Promotion
import ru.coffeerun.model.PromotionType
import ru.coffeerun.model.Recipe
import ru.coffeerun.model.MAX_NOTE_LENGTH
import ru.coffeerun.model.MIN_QUANTITY
import ru.coffeerun.ui.menu.MenuViewModel
import ru.coffeerun.ui.cart.CartViewModel

/** Форма напитка: правила выбора, цена и строка, которая уходит в корзину. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val cart = InMemoryCartRepository()
    private var savedState = SavedStateHandle()
    private fun vm(product: Product) = vm(product.id)
    private fun vm(id: String) = ProductViewModel(id, DemoMenuRepository(), cart, InMemoryFavorites(), savedState)

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
            override suspend fun refresh() = ru.coffeerun.model.MenuSnapshot(getMenu(), getOptionPrices())

            override suspend fun getMenu(): List<Product> = listOfNotNull(gate.await())
            override suspend fun getProduct(id: String): Product? = getMenu().firstOrNull { it.id == id }
            override suspend fun getOptionPrices() = DemoMenu.prices
        }
        val vm = ProductViewModel(DemoMenu.LATTE.id, slow, cart, InMemoryFavorites(), savedState)
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
            override suspend fun refresh() = ru.coffeerun.model.MenuSnapshot(getMenu(), getOptionPrices())

            override suspend fun getMenu(): List<Product> = DemoMenu.products
            override suspend fun getProduct(id: String): Product? = DemoMenu.byId(id)
            override suspend fun getOptionPrices() = prices
        }
        val vm = ProductViewModel(DemoMenu.LATTE.id, repo, cart, InMemoryFavorites(), savedState)
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
    fun recipeUsesCurrentDrinkOptionsWithoutAddingCartQuantity() = runTest {
        val recipes = object : RecipeRepository {
            val recipes = MutableStateFlow<List<Recipe>>(emptyList())
            override fun observe(session: AuthSession) = recipes
            override suspend fun find(id: String, session: AuthSession) = recipes.value.firstOrNull()
            override suspend fun refresh(session: AuthSession) = Unit
            override suspend fun rename(id: String, name: String, session: AuthSession): Recipe? = error("Unused")
            override suspend fun remove(id: String, session: AuthSession) = Unit
            override suspend fun save(recipe: Recipe, session: AuthSession) { recipes.value = listOf(recipe) }
        }
        val auth = InMemoryAuthRepository(loggedIn = true)
        val product = DemoMenu.CAPPUCCINO
        val vm = ProductViewModel(
            product.id, DemoMenuRepository(), cart, InMemoryFavorites(), savedState, recipes, auth
        )
        vm.selectSize(DrinkSize.LARGE)
        vm.selectMilk(MilkType.OAT)
        vm.toggleExtra(Extra.VANILLA)
        vm.setIced(true)
        vm.setNote(" Less foam ")
        vm.changeQuantity(3)
        vm.saveRecipe(" Morning ")
        val recipe = recipes.recipes.value.single()
        assertEquals("Morning", recipe.name)
        assertEquals(product.defaultOptions()?.copy(
            size = DrinkSize.LARGE, milk = MilkType.OAT, extras = setOf(Extra.VANILLA), isIced = true, note = "Less foam"
        ), recipe.options)
        assertEquals(emptyList<CartLine>(), cart.lines.value)

        val editor = ProductViewModel(
            product.id, DemoMenuRepository(), cart, InMemoryFavorites(), SavedStateHandle(), recipes, auth, recipeId = recipe.id
        )
        keepActive(editor)
        val state = editor.uiState.value as ProductUiState.Success
        assertEquals("Morning", state.recipeName)
        assertEquals(MIN_QUANTITY, state.form.quantity)
        assertEquals("Less foam", state.form.note)
        editor.selectSize(DrinkSize.SMALL)
        editor.saveRecipe("Short")
        assertEquals(recipe.id, recipes.recipes.value.single().id)
        assertEquals(DrinkSize.SMALL, recipes.recipes.value.single().options?.size)
    }

    @Test
    fun cartEditorStartsFromExistingOptionsAndReplacesTheLineAtCurrentPrice() = runTest {
        val product = DemoMenu.CAPPUCCINO
        val options = checkNotNull(product.defaultOptions()).copy(size = DrinkSize.LARGE, milk = MilkType.OAT)
        cart.addLine(CartLine(product, 200, 3, options))
        val vm = ProductViewModel(
            product.id, DemoMenuRepository(), cart, InMemoryFavorites(), savedState,
            sessions = InMemoryAuthRepository(), cartLineId = cart.lines.value.single().id
        )
        keepActive(vm)
        val state = vm.uiState.value as ProductUiState.Success
        assertEquals(DrinkSize.LARGE, state.form.size)
        assertEquals(MilkType.OAT, state.form.milk)
        assertEquals(3, state.form.quantity)
        vm.selectSize(DrinkSize.SMALL)
        vm.addToCart()
        val updated = cart.lines.value.single()
        assertEquals(3, updated.quantity)
        assertEquals(260, updated.unitPrice)
        assertEquals(options.copy(size = DrinkSize.SMALL), updated.options)
    }

    @Test
    fun mediumCappuccinoShowsPromotionWithoutChangingStoredUnitPrice() = runTest {
        val product = DemoMenu.CAPPUCCINO
        val demo = DemoMenuRepository()
        val snapshot = MenuSnapshot(
            listOf(product), DemoMenu.prices, promotions = listOf(
                Promotion("medium", PromotionType.CAPPUCCINO_MEDIUM, 0, Long.MAX_VALUE, product.id)
            )
        )
        val menu = object : MenuRepository by demo {
            override fun observeSnapshot() = kotlinx.coroutines.flow.flowOf(snapshot)
        }
        val vm = ProductViewModel(product.id, menu, cart, InMemoryFavorites(), savedState)
        keepActive(vm)
        vm.selectMilk(MilkType.OAT)

        val medium = vm.uiState.value as ProductUiState.Success
        assertEquals(300, medium.unitPrice)
        assertEquals(52, medium.discount)
        vm.toggleExtra(Extra.VANILLA)
        vm.changeQuantity(+1)
        assertEquals(576L, (vm.uiState.value as ProductUiState.Success).totalPrice)
        vm.addToCart()
        assertEquals(340, cart.lines.value.single().unitPrice)
        assertEquals(0, cart.lines.value.single().discount)

        vm.selectSize(DrinkSize.LARGE)
        assertEquals(0, (vm.uiState.value as ProductUiState.Success).discount)
    }

    @Test
    fun pastryBundleUsesSelectedQuantityForTotalAndOnePortionForHeader() = runTest {
        val product = DemoMenu.CROISSANT
        val snapshot = MenuSnapshot(
            listOf(product), DemoMenu.prices, promotions = listOf(
                Promotion("pastry", PromotionType.PASTRY_2_PLUS_1, 0, Long.MAX_VALUE, category = ProductCategory.PASTRY)
            )
        )
        val menu = object : MenuRepository by DemoMenuRepository() {
            override fun observeSnapshot() = kotlinx.coroutines.flow.flowOf(snapshot)
        }
        val vm = ProductViewModel(product.id, menu, cart, InMemoryFavorites(), savedState)
        keepActive(vm)
        for (expected in listOf(360L, 360L, 540L)) {
            vm.changeQuantity(+1)
            val state = vm.uiState.value as ProductUiState.Success
            assertEquals(180, state.unitPrice)
            assertEquals(0, state.discount)
            assertEquals(expected, state.totalPrice)
        }
        vm.changeQuantity(-1)
        vm.addToCart()
        val cartVm = CartViewModel(cart, menu).apply { onShown() }
        val checkout = testCheckout(cart, InMemoryOrders(), InMemoryShopRepository(), menu = menu)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cartVm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { checkout.uiState.collect {} }
        assertEquals(360, cartVm.uiState.value.total)
        assertEquals(360, checkout.uiState.value.total)
    }

    @Test
    fun addingThreePortionsToNineReportsTheLimitAndKeepsNine() = runTest {
        val product = DemoMenu.CROISSANT
        cart.add(CartLine(product, product.price, quantity = 9))
        val vm = vm(product)
        keepActive(vm)
        vm.changeQuantity(2)
        val results = mutableListOf<Int?>()
        vm.addToCart { results.add(it) }

        assertEquals(listOf(R.string.cart_add_limit), results)
        assertEquals(9, cart.lines.value.single().quantity)
    }

    @Test
    fun form_defaults() {
        assertEquals(ProductForm(), vm(DemoMenu.CAPPUCCINO).form.value)
    }
}
