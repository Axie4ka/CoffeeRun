package ru.coffeerun

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import ru.coffeerun.data.LocalMenu

/** «Поделиться» на экране позиции открывает системный выбор приложения с текстом о позиции. */
@RunWith(AndroidJUnit4::class)
class ShareTest {
    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            (app as CoffeeRunApplication).container = AppContainer()
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    @Test
    fun share_launchesChooserWithProductText() {
        // Чузер не должен открываться по-настоящему
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        val name = rule.activity.getString(LocalMenu.LATTE.nameRes)

        rule.onNodeWithText(name).performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_share)).performClick()

        intended(
            allOf(
                hasAction(Intent.ACTION_CHOOSER),
                hasExtra(
                    equalTo(Intent.EXTRA_INTENT),
                    allOf(
                        hasAction(Intent.ACTION_SEND),
                        hasType("text/plain"),
                        hasExtra(equalTo(Intent.EXTRA_TEXT), containsString(name))
                    )
                )
            )
        )
    }
}
