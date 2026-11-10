package ru.coffeerun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.*
import ru.coffeerun.model.CartLine
import ru.coffeerun.model.Shop

@RunWith(AndroidJUnit4::class)
class CheckoutPolicyUiTest {
    @Volatile private var nowMillis = Instant.parse("2026-11-10T09:00:00Z").toEpochMilli()
    private lateinit var previousZone: TimeZone
    private lateinit var container: AppContainer
    private lateinit var orders: InMemoryOrders

    @get:Rule(order = 0)
    val fixedClock = object : ExternalResource() {
        override fun before() {
            previousZone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            orders = InMemoryOrders(now = { nowMillis })
            container = AppContainer(DemoMenuRepository(), InMemoryAuthRepository(loggedIn = true),
                InMemoryProfileRepository(), InMemoryFavorites(), orders, pickupPolicy = testPickupPolicy { nowMillis })
            container.cart.add(CartLine(DemoMenu.CROISSANT, 180))
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoffeeRunApplication
            app.container = container
        }
        override fun after() { TimeZone.setDefault(previousZone) }
    }
    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun openCheckout() {
        rule.onNode(hasText(str(R.string.tab_cart)) and hasClickAction()).performClick()
        rule.onNodeWithText(str(R.string.cart_checkout)).performClick()
    }

    @Test
    fun readyTime_usesShopZone_inConfirmationListAndDetails() {
        openCheckout()
        rule.onNodeWithText(str(R.string.checkout_confirm)).performClick()
        rule.onNodeWithText(str(R.string.placed_ready, "12:15")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.placed_to_orders)).performClick()
        rule.onNodeWithText(str(R.string.order_meta_active, "12:15", str(Shop.MOSKOVSKAYA.addressRes))).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.order_title, 1042)).performClick()
        rule.onNodeWithText(str(R.string.order_time_active, "12:15")).assertIsDisplayed()
    }

    @Test
    fun asapExpiresBeforeConfirmation_keepsCartAndExplains() {
        openCheckout()
        rule.runOnIdle { nowMillis = Instant.parse("2026-11-10T18:50:00Z").toEpochMilli() }
        rule.onNodeWithText(str(R.string.checkout_confirm)).performClick()
        rule.onNodeWithText(str(R.string.pickup_time_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.checkout_confirm)).assertIsDisplayed()
        check(container.cart.lines.value.size == 1)
        check(orders.orders.value.size == 2)
    }
}
