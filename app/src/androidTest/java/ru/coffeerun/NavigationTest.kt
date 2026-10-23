package ru.coffeerun

import ru.coffeerun.data.LocalMenu
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.model.DEFAULT_DRINK_SIZE
import ru.coffeerun.model.MilkType
import ru.coffeerun.model.Product
import ru.coffeerun.model.Shop
import ru.coffeerun.model.unitPrice

/**
 * Сценарии навигации из плана (F1–F12, N1). Проверяют поведение через UI,
 * поэтому не зависят от того, как навигация устроена внутри.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    // Репозитории живут в Application, а процесс у всех тестов общий: свежие данные — до запуска Activity
    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            (app as CoffeeRunApplication).container = AppContainer()
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    // Тексты из ресурсов: тест проходит и на русской, и на английской локали
    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    // Пункт нижней панели. Заголовок TopAppBar с тем же текстом не кликабелен
    private fun tab(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    private fun assertMenuShown() {
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
    }

    private fun openProduct(product: Product) {
        rule.onNodeWithText(str(product.nameRes)).performClick()
    }

    private fun placeCappuccinoOrder() {
        val name = str(LocalMenu.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        rule.onNodeWithText(str(R.string.checkout_confirm)).performClick()
    }

    @Test // F1, F2
    fun tab_backReturnsToMenu() {
        tab(R.string.tab_cart).performClick()
        tab(R.string.tab_cart).assertIsSelected()
        rule.onNodeWithText(str(R.string.cart_empty)).assertIsDisplayed()
        Espresso.pressBack()
        assertMenuShown()
        tab(R.string.tab_menu).assertIsSelected()
    }

    @Test // F4
    fun tab_reselectDoesNotStack() {
        repeat(3) { tab(R.string.tab_orders).performClick() }
        Espresso.pressBack()
        assertMenuShown()
    }

    @Test // F5, F6
    fun product_hidesBottomBarAndBackReturnsToMenu() {
        openProduct(LocalMenu.CAPPUCCINO)
        tab(R.string.tab_orders).assertDoesNotExist()
        Espresso.pressBack()
        tab(R.string.tab_orders).assertIsDisplayed()
        assertMenuShown()
    }

    @Test
    fun product_addToCartReturnsToMenu() {
        val cappuccino = LocalMenu.CAPPUCCINO
        openProduct(cappuccino)
        val price = cappuccino.unitPrice(DEFAULT_DRINK_SIZE, MilkType.REGULAR, emptySet())
        rule.onNodeWithText(str(R.string.add_to_cart, price)).performClick()
        assertMenuShown()
        tab(R.string.tab_menu).assertIsSelected()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).assertIsDisplayed()
    }

    @Test // F7
    fun productCart_selectsCartTab() {
        openProduct(LocalMenu.CAPPUCCINO)
        rule.onNodeWithContentDescription(str(R.string.action_cart)).performClick()
        tab(R.string.tab_cart).assertIsSelected()
    }

    @Test // F8
    fun checkout_backReturnsToCart() {
        val name = str(LocalMenu.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        tab(R.string.tab_cart).assertDoesNotExist()
        Espresso.pressBack()
        tab(R.string.tab_cart).assertIsSelected()
    }

    @Test // F9, F10
    fun placed_backGoesToMenu() {
        placeCappuccinoOrder()
        rule.onNodeWithText(str(R.string.placed_title)).assertIsDisplayed()
        Espresso.pressBack()
        assertMenuShown()
        rule.onNodeWithText(str(R.string.checkout_title)).assertDoesNotExist()
    }

    @Test // F10
    fun placed_toMenuButtonGoesToMenu() {
        placeCappuccinoOrder()
        rule.onNodeWithText(str(R.string.placed_to_menu)).performClick()
        assertMenuShown()
        tab(R.string.tab_menu).assertIsSelected()
    }

    @Test // F11
    fun placed_toOrdersOpensOrdersTab() {
        placeCappuccinoOrder()
        rule.onNodeWithText(str(R.string.placed_to_orders)).performClick()
        tab(R.string.tab_orders).assertIsSelected()
    }

    @Test // Ошибка «openTab с экрана поверх вкладок» из плана, раздел 7
    fun placed_toOrdersThenMenuTabDoesNotRestorePlaced() {
        placeCappuccinoOrder()
        rule.onNodeWithText(str(R.string.placed_to_orders)).performClick()
        tab(R.string.tab_menu).performClick()
        assertMenuShown()
        rule.onNodeWithText(str(R.string.placed_title)).assertDoesNotExist()
    }

    @Test // F12: в демо-истории два заказа, повторяем первый
    fun ordersRepeat_opensCartTab() {
        tab(R.string.tab_orders).performClick()
        rule.onAllNodesWithText(str(R.string.order_repeat))[0].performClick()
        tab(R.string.tab_cart).assertIsSelected()
        rule.onNodeWithText(str(R.string.cart_checkout)).assertIsDisplayed()
    }

    @Test // N1
    fun recreate_keepsProductScreen() {
        openProduct(LocalMenu.LATTE)
        rule.activityRule.scenario.recreate()
        tab(R.string.tab_orders).assertDoesNotExist()
        rule.onNodeWithText(str(LocalMenu.LATTE.nameRes)).assertIsDisplayed()
    }

    // ---------- Поведение, которое добавил редизайн M3 (спецификация 2026-10-20, раздел 9) ----------

    @Test
    fun cartEmpty_browseMenuOpensMenu() {
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_empty)).assertIsDisplayed()
        tab(R.string.browse_menu).performClick()
        assertMenuShown()
        tab(R.string.tab_menu).assertIsSelected()
    }

    @Test
    fun checkout_scheduledPickupOpensTimePicker() {
        val name = str(LocalMenu.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        rule.onNodeWithText(str(R.string.pickup_asap)).assertIsSelected()
        rule.onNodeWithText(str(R.string.pickup_scheduled)).performClick()
        // Заголовок диалога совпадает с заголовком секции, поэтому проверяем по кнопке «ОК»
        rule.onNodeWithText(str(android.R.string.ok)).assertIsDisplayed()
    }

    @Test
    fun product_minusDisabledAtOne() {
        openProduct(LocalMenu.CAPPUCCINO)
        rule.onNodeWithContentDescription(str(R.string.action_decrease)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(str(R.string.action_increase)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_decrease)).assertIsEnabled()
    }

    @Test
    fun menu_tabsSwitchCategory() {
        rule.onNodeWithText(str(LocalMenu.CAPPUCCINO.nameRes)).assertIsDisplayed()
        tab(R.string.category_pastry).performClick()
        rule.onNodeWithText(str(LocalMenu.CROISSANT.nameRes)).assertIsDisplayed()
        rule.onNodeWithText(str(LocalMenu.CAPPUCCINO.nameRes)).assertDoesNotExist()
    }

    @Test
    fun favorites_flow() {
        val latte = LocalMenu.LATTE
        openProduct(latte)
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_unfavorite)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        tab(R.string.tab_profile).performClick()
        rule.onNodeWithText(str(R.string.profile_favorites)).performClick()
        rule.onNodeWithText(str(latte.nameRes)).assertIsDisplayed()
        rule.onNodeWithText(str(latte.nameRes)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_unfavorite)).assertIsDisplayed()
    }

    @Test
    fun profile_aboutDialogOpensAndCloses() {
        tab(R.string.tab_profile).performClick()
        rule.onNodeWithText(str(R.string.profile_about)).performClick()
        rule.onNodeWithText(str(R.string.about_text)).assertIsDisplayed()
        rule.onNodeWithText(str(android.R.string.ok)).performClick()
        rule.onNodeWithText(str(R.string.about_text)).assertDoesNotExist()
    }

    @Test
    fun shopPicker_changesCheckoutShop() {
        rule.onNodeWithText(str(Shop.MOSKOVSKAYA.addressRes)).performClick()
        rule.onNodeWithText(str(Shop.STROITELEY.addressRes)).performClick()
        val name = str(LocalMenu.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        tab(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        rule.onNodeWithText(str(Shop.STROITELEY.addressRes)).assertIsDisplayed()
    }
}
