package com.example.resouretree

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun findWeb(view: View): WebView? = if (view is WebView) view else
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { findWeb(group.getChildAt(it)) } }

    @Test fun realHttpsPageLoadsHistorySurvivesRecreationAndCloseFinishes() {
        compose.setContent { Text("网页验证") }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<WebPageActivity>(Intent(context, WebPageActivity::class.java).putExtra("url", "https://example.com/")).use { scenario ->
            fun awaitPage(expected: String) {
                compose.waitUntil(45000) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val view = findWeb(activity.window.decorView)
                        ready = view?.progress == 100 && view.url == expected && view.title == "Example Domain"
                    }
                    ready
                }
            }
            awaitPage("https://example.com/")
            scenario.onActivity { activity ->
                val view = requireNotNull(findWeb(activity.window.decorView))
                assertFalse(view.settings.allowFileAccess); assertFalse(view.settings.allowContentAccess)
                view.loadUrl("https://example.com/?page=2")
            }
            awaitPage("https://example.com/?page=2")
            scenario.recreate()
            awaitPage("https://example.com/?page=2")
            compose.onNodeWithText("返回", substring = false).performClick()
            awaitPage("https://example.com/")
            compose.onNodeWithText("刷新", substring = false).performClick()
            awaitPage("https://example.com/")
            compose.onNodeWithText("关闭", substring = false).performClick()
            // ActivityScenario launches its own task; closing it need not resume the test host.
            compose.waitUntil(10000) { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        }
    }
}
