package team.bjtuss.bjtuselfservice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import team.bjtuss.bjtuselfservice.statemanager.AppState
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager

@RunWith(AndroidJUnit4::class)
class NavigationInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationButtonsStayOutsideSystemNavigationAndRemainClickable() {
        // Layout verification must not depend on a live school account.
        compose.runOnUiThread { AppStateManager.updateState(AppState.Idle) }
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)!!
            .getInsets(WindowInsetsCompat.Type.navigationBars())
        val left = insets.left.toFloat()
        val right = (decor.width - insets.right).toFloat()
        val bottom = (decor.height - insets.bottom).toFloat()
        for (label in listOf("应用", "设置", "首页")) {
            val button = compose.onNodeWithContentDescription(label)
            button.assertIsDisplayed()
            val bounds = button.fetchSemanticsNode().boundsInWindow
            assertTrue("$label overlaps system navigation: $bounds / $bottom", bounds.bottom <= bottom)
            assertTrue("$label overlaps left system navigation", bounds.left >= left)
            assertTrue("$label overlaps right system navigation", bounds.right <= right)
            button.performClick()
            compose.waitForIdle()
        }
    }
}
