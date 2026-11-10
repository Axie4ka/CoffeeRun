package ru.coffeerun

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryFavorites
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.NetworkAuthRepository
import ru.coffeerun.data.NetworkOrderRepository
import ru.coffeerun.domain.orders.OrdersCoordinator
import ru.coffeerun.data.local.TokenStore
import ru.coffeerun.data.network.OrderDto
import ru.coffeerun.data.network.OrderLineDto
import ru.coffeerun.model.OrderStatus
import ru.coffeerun.model.CartLine
import ru.coffeerun.data.DemoMenu
import androidx.test.espresso.Espresso
import org.junit.Assert.assertTrue

/** Настоящие TokenStore и репозиторий заказов; только HTTP заменён управляемыми ответами. */
@RunWith(AndroidJUnit4::class)
class OrderSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var tokenFile: File
    private lateinit var tokens: TokenStore
    private lateinit var orders: OrdersCoordinator
    private lateinit var auth: NetworkAuthRepository
    @Volatile private var status = "PICKED_UP"
    @Volatile private var listFails = false
    @Volatile private var listGate: CompletableDeferred<Unit>? = null
    @Volatile private var listCalls = 0

    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            tokenFile = File(context.cacheDir, "order-session-${UUID.randomUUID()}.preferences_pb")
            tokens = TokenStore(PreferenceDataStoreFactory.create(scope = scope) { tokenFile })
            runBlocking { tokens.save("test-token") }
            val api = object : StubApi() {
                private fun order() = OrderDto(
                    1051, status, "MOSKOVSKAYA", "2026-11-03T12:00:00+03:00",
                    lines = listOf(OrderLineDto("croissant", 2, 180))
                )
                override suspend fun getOrders(): List<OrderDto> {
                    listCalls++
                    listGate?.await()
                    if (listFails) throw IOException("offline")
                    return listOf(order())
                }
                override suspend fun getOrder(number: Int): OrderDto {
                    check(number == 1051)
                    return order()
                }
            }
            val menu = DemoMenuRepository()
            auth = NetworkAuthRepository(api, tokens)
            val pickupPolicy = testPickupPolicy()
            orders = OrdersCoordinator(NetworkOrderRepository(api, menu, tokens), tokens, scope, pickupPolicy)
            (context.applicationContext as CoffeeRunApplication).container = AppContainer(
                menu, auth, InMemoryProfileRepository(), InMemoryFavorites(), orders, pickupPolicy = pickupPolicy
            )
        }

        override fun after() {
            scope.cancel()
            tokenFile.delete()
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun openDetails() {
        rule.onNode(hasText(str(R.string.tab_orders)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) { orders.find(1051) != null }
        rule.onNodeWithText(str(R.string.order_title, 1051)).performClick()
        rule.onNodeWithText(str(R.string.status_picked_up)).assertIsDisplayed()
    }

    @Test
    fun logout_removesPersonalDataFromOpenDetails() {
        openDetails()
        val cart = (rule.activity.application as CoffeeRunApplication).container.cart
        rule.runOnIdle { cart.add(CartLine(DemoMenu.CROISSANT, 180)) }
        runBlocking { auth.logout() }
        rule.waitUntil(5_000) { orders.orders.value.isEmpty() }
        rule.onNodeWithText(str(R.string.status_picked_up)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.line_summary, "Круассан", 2)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        assertTrue(cart.lines.value.isEmpty())
        // Сохранённая вкладка тоже не должна восстановить прежние детали после выхода.
        rule.onNode(hasText(str(R.string.tab_orders)) and hasClickAction()).performClick()
        rule.onNodeWithText(str(R.string.order_title, 1051)).assertDoesNotExist()
        Espresso.pressBack()
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun logoutAfterActivityClosed_clearsApplicationCart() {
        val cart = (rule.activity.application as CoffeeRunApplication).container.cart
        rule.runOnIdle { cart.add(CartLine(DemoMenu.CROISSANT, 180)) }
        rule.activityRule.scenario.close()
        runBlocking { auth.logout() }
        rule.waitUntil(5_000) { cart.lines.value.isEmpty() }
        assertTrue(cart.lines.value.isEmpty())
    }

    @Test
    fun logoutDuringRecreation_doesNotRestoreEditProfile() {
        rule.onNode(hasText(str(R.string.tab_profile)) and hasClickAction()).performClick()
        rule.onNodeWithContentDescription(str(R.string.profile_edit)).performClick()
        rule.onNodeWithText(str(R.string.profile_edit_title)).assertIsDisplayed()
        val oldActivity = rule.activity
        val app = oldActivity.application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                if (activity === oldActivity) runBlocking { auth.logout() }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
            rule.onNodeWithText(str(R.string.profile_edit_title)).assertDoesNotExist()
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    @Test
    fun sameTokenNewLogin_clearsOldNavigation() {
        openDetails()
        status = "READY"
        runBlocking { tokens.save("test-token") }
        rule.waitUntil(5_000) { !orders.isLoaded.value && orders.find(1051) == null }
        rule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.status_ready)).assertDoesNotExist()
        rule.onNode(hasText(str(R.string.tab_orders)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) { orders.find(1051)?.status == OrderStatus.READY }
        rule.onNodeWithText(str(R.string.status_ready)).assertIsDisplayed()
    }
    @Test
    fun profile_countFailure_canRetryFromProfile() {
        rule.runOnIdle { listFails = true }
        rule.onNode(hasText(str(R.string.tab_profile)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(str(R.string.action_retry)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("—").assertIsDisplayed()
        rule.runOnIdle { listFails = false }
        rule.onNodeWithText(str(R.string.action_retry)).performClick()
        rule.waitUntil(5_000) { orders.isLoaded.value }
        rule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun profile_loadsHistoryWithoutOpeningOrders_countIsUnknownUntilReply() {
        val reply = CompletableDeferred<Unit>()
        rule.runOnIdle { listGate = reply }
        check(!orders.isLoaded.value)
        rule.onNode(hasText(str(R.string.tab_profile)) and hasClickAction()).performClick()
        rule.waitUntil(5_000) { listCalls == 1 }
        rule.onNodeWithText("—").assertIsDisplayed()
        reply.complete(Unit)
        rule.waitUntil(5_000) { orders.isLoaded.value }
        rule.onNodeWithText("1").assertIsDisplayed()
    }

}
