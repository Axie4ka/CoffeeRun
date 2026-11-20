package ru.coffeerun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.domain.favorites.Favorites
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository

/** Сервер не принял сердечко: оно откатывается, а снэкбар объясняет почему (и не пропадает сразу). */
@RunWith(AndroidJUnit4::class)
class FavoritesErrorTest {
    /** Сервер избранного, который не принимает изменения — как при выключенном Mockoon. */
    private class RejectingFavorites : Favorites {
        override val ids = MutableStateFlow(emptySet<String>())
        override suspend fun refresh() = Unit
        override suspend fun toggle(productId: String) = false
    }

    @get:Rule(order = 0)
    val offlineFavorites = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            (app as CoffeeRunApplication).container = AppContainer(
                menu = DemoMenuRepository(),
                auth = InMemoryAuthRepository(loggedIn = true),
                profile = InMemoryProfileRepository(),
                favorites = RejectingFavorites(),
                orders = InMemoryOrders(), pickupPolicy = testPickupPolicy()
            )
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    @Test
    fun rejectedHeart_showsSnackbar_andStaysOff() {
        rule.onNodeWithText(DemoMenu.LATTE.name).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).performClick()
        rule.onNodeWithText(str(R.string.favorites_toggle_error)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).assertIsDisplayed()
    }
}
