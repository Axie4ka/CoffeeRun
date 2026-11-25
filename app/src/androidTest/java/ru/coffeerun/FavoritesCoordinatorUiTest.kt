package ru.coffeerun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.DemoMenu
import ru.coffeerun.data.DemoMenuRepository
import ru.coffeerun.data.InMemoryAuthRepository
import ru.coffeerun.data.InMemoryOrders
import ru.coffeerun.data.InMemoryProfileRepository
import ru.coffeerun.data.repository.NetworkFavoritesRepository
import ru.coffeerun.data.session.AuthSession
import ru.coffeerun.data.session.SessionChangedException
import ru.coffeerun.data.session.SessionProvider
import ru.coffeerun.domain.favorites.FavoritesCoordinator

/** Настоящий координатор и репозиторий; задерживается только ответ API. */
@RunWith(AndroidJUnit4::class)
class FavoritesCoordinatorUiTest {
    private lateinit var scope: CoroutineScope
    private lateinit var coordinator: FavoritesCoordinator
    private lateinit var repository: NetworkFavoritesRepository
    private lateinit var reply: CompletableDeferred<Unit>
    @Volatile private var reject = false

    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            reply = CompletableDeferred()
            reject = false
            val session = AuthSession(0, true)
            val sessions = object : SessionProvider {
                private val guard = Any()
                override val sessions = MutableStateFlow(session)
                override suspend fun currentSession() = session
                override fun <T> withSession(session: AuthSession, action: () -> T): T = synchronized(guard) {
                    if (session != this.sessions.value) throw SessionChangedException()
                    action()
                }
            }
            val server = mutableSetOf<String>()
            val api = object : StubApi() {
                override suspend fun getFavorites() = server.toList()
                override suspend fun addFavorite(productId: String) {
                    reply.await()
                    if (reject) throw IOException("offline")
                    server += productId
                }
            }
            repository = NetworkFavoritesRepository(api, sessions)
            coordinator = FavoritesCoordinator(repository, sessions, scope)
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CoffeeRunApplication
            app.container = AppContainer(
                DemoMenuRepository(), InMemoryAuthRepository(loggedIn = true),
                InMemoryProfileRepository(), coordinator, InMemoryOrders(), pickupPolicy = testPickupPolicy()
            )
        }

        override fun after() { scope.cancel() }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    @Test
    fun pendingHeart_survivesScreenChange_andAppearsInFavoritesAfterReply() {
        rule.onNodeWithText(DemoMenu.LATTE.name).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_unfavorite)).assertIsDisplayed()
        check(repository.confirmedIds.value.isEmpty())
        rule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        rule.onNode(hasText(str(R.string.tab_profile)) and hasClickAction()).performClick()
        rule.onNodeWithText(str(R.string.profile_favorites)).performClick()
        check(coordinator.ids.value == setOf(DemoMenu.LATTE.id))
        reply.complete(Unit)
        rule.waitUntil(5_000) { repository.confirmedIds.value == setOf(DemoMenu.LATTE.id) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(DemoMenu.LATTE.name).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(DemoMenu.LATTE.name).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_unfavorite)).assertIsDisplayed()
    }

    @Test
    fun rejectedReply_rollsBackHeart_andShowsError() {
        reject = true
        rule.onNodeWithText(DemoMenu.LATTE.name).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_unfavorite)).assertIsDisplayed()
        reply.complete(Unit)
        rule.waitUntil(5_000) { coordinator.ids.value.isEmpty() }
        rule.onNodeWithContentDescription(str(R.string.action_favorite)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.favorites_toggle_error)).assertIsDisplayed()
    }
}
