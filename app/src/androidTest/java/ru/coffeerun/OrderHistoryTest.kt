package ru.coffeerun

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.MenuRepository
import ru.coffeerun.domain.orders.Orders
import ru.coffeerun.model.Order
import ru.coffeerun.model.OrderItem
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.Shop

/** История сохраняет неизвестные товары, а ожидающий повтор принадлежит открытому экрану. */
@RunWith(AndroidJUnit4::class)
class OrderHistoryTest {
    private val menuReply = CompletableDeferred<Unit>()
    private var delayMenu = false
    private var menuWaiting = false
    private lateinit var container: AppContainer
    private lateinit var historyState: MutableStateFlow<List<Order>>

    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            val menu = object : MenuRepository by DemoMenuRepository() {
                override suspend fun getMenu() = DemoMenuRepository().getMenu().also {
                    if (delayMenu) {
                        menuWaiting = true
                        menuReply.await()
                    }
                }
            }
            val order = Order(
                1050, OrderStatus.PICKED_UP,
                listOf(OrderItem("croissant", "Круассан", 180), OrderItem("retired", null, 320, quantity = 2)),
                Shop.MOSKOVSKAYA, 0L
            )
            historyState = MutableStateFlow(listOf(order))
            val history = object : Orders by InMemoryOrders() {
                override val orders = historyState
                override fun observeOrder(number: Int) = orders.map { list -> list.find { it.number == number } }
                override fun find(number: Int) = orders.value.find { it.number == number }
                override suspend fun load(number: Int) = find(number)
            }
            container = AppContainer(
                menu, InMemoryAuthRepository(loggedIn = true), InMemoryProfileRepository(),
                InMemoryFavorites(), history, pickupPolicy = testPickupPolicy()
            )
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoffeeRunApplication
            app.container = container
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun clickable(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    private fun openDetails() {
        clickable(R.string.tab_orders).performClick()
        rule.onNodeWithText(str(R.string.order_title, 1050)).performClick()
    }

    @Test
    fun unknownItem_detailsShowQuantityAndPaidTotal() {
        openDetails()
        rule.onNodeWithText(str(R.string.line_summary, str(R.string.order_item_unavailable), 2)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.price_rub, 640)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.price_rub, 820)).assertIsDisplayed()
    }

    @Test
    fun delayedRepeat_backCancelsWithoutChangingCartOrTab() {
        openDetails()
        rule.runOnIdle { delayMenu = true }
        clickable(R.string.order_repeat).performClick()
        rule.waitUntil(5_000) { menuWaiting }
        Espresso.pressBack()
        rule.waitForIdle()
        rule.runOnIdle { menuReply.complete(Unit) }
        rule.waitForIdle()
        clickable(R.string.tab_orders).assertIsSelected()
        rule.runOnIdle { assertTrue("уход со страницы отменяет ожидающий повтор", container.cart.lines.value.isEmpty()) }
    }

    @Test
    fun repositoryStatusChange_updatesOpenDetails() {
        openDetails()
        rule.onNodeWithText(str(R.string.status_picked_up)).assertIsDisplayed()
        rule.runOnIdle {
            historyState.value = historyState.value.map { it.copy(status = OrderStatus.READY) }
        }
        rule.onNodeWithText(str(R.string.status_ready)).assertIsDisplayed()
    }
}
