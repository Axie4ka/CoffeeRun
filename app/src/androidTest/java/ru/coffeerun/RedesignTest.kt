package ru.coffeerun

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.coffeerun.model.Product

/** Поведение, которое добавил редизайн M3 (спецификация 2026-10-20, раздел 9). */
@RunWith(AndroidJUnit4::class)
class RedesignTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun clickable(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    @Test
    fun cartEmpty_browseMenuOpensMenu() {
        clickable(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_empty)).assertIsDisplayed()
        clickable(R.string.browse_menu).performClick()
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        clickable(R.string.tab_menu).assertIsSelected()
    }

    @Test
    fun checkout_scheduledPickupIsDisabled() {
        val name = str(Product.CAPPUCCINO.nameRes)
        rule.onNodeWithContentDescription(str(R.string.add_product, name)).performClick()
        clickable(R.string.tab_cart).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
        rule.onNodeWithText(str(R.string.pickup_scheduled)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.pickup_asap)).assertIsSelected()
    }

    @Test
    fun product_minusDisabledAtOne() {
        rule.onNodeWithText(str(Product.CAPPUCCINO.nameRes)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_decrease)).assertIsNotEnabled()
        rule.onNodeWithContentDescription(str(R.string.action_increase)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_decrease)).assertIsEnabled()
    }

    @Test
    fun menu_tabsSwitchCategory() {
        rule.onNodeWithText(str(Product.CAPPUCCINO.nameRes)).assertIsDisplayed()
        clickable(R.string.category_pastry).performClick()
        rule.onNodeWithText(str(Product.CROISSANT.nameRes)).assertIsDisplayed()
        rule.onNodeWithText(str(Product.CAPPUCCINO.nameRes)).assertDoesNotExist()
    }
}
