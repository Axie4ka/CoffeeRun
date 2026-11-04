package ru.coffeerun

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryFavoritesRepository
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.InMemoryOrderRepository
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository

/** Гость: оформление, заказы и избранное требуют входа; после входа из корзины сразу открывается оформление. */
@RunWith(AndroidJUnit4::class)
class GuestTest {
    @get:Rule(order = 0)
    val guest = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            (app as CoffeeRunApplication).container = AppContainer(menu = DemoMenuRepository(), auth = InMemoryAuthRepository(loggedIn = false), profile = InMemoryProfileRepository(), favorites = InMemoryFavoritesRepository(), orders = InMemoryOrderRepository())
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun clickable(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    private fun assertLoginShown() = rule.onNodeWithText(str(R.string.auth_login_subtitle)).assertIsDisplayed()

    @Test
    fun orders_showSignInPrompt() {
        clickable(R.string.tab_orders).performClick()
        rule.onNodeWithText(str(R.string.orders_guest_title)).assertIsDisplayed()
        clickable(R.string.auth_login_button).performClick()
        assertLoginShown()
    }

    @Test
    fun checkout_asGuest_signIn_thenCheckoutOpens() {
        rule.onNodeWithContentDescription(str(R.string.add_product, DemoMenu.CAPPUCCINO.name)).performClick()
        clickable(R.string.tab_cart).performClick()
        clickable(R.string.cart_checkout).performClick()
        assertLoginShown()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("9990000000")
        fields[1].performTextInput(InMemoryAuthRepository.DEMO_PASSWORD)
        clickable(R.string.auth_login_button).performClick()
        rule.onNodeWithText(str(R.string.checkout_confirm)).assertIsDisplayed()
    }

    @Test
    fun favorite_asGuest_opensSignIn() {
        rule.onNodeWithText(DemoMenu.LATTE.name).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).performClick()
        assertLoginShown()
        // Сердечко само не ставится: после входа пользователь нажмёт ещё раз
        rule.onAllNodesWithContentDescription(str(R.string.action_unfavorite)).fetchSemanticsNodes().let { assert(it.isEmpty()) }
    }
}
