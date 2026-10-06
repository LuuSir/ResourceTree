package com.example.resouretree

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.overlay.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class FloatingEntrySizeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun sliderResizesRealOverlayWindowAndKeepsPreferenceAfterRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        assumeTrue("Existing overlay permission is required", Settings.canDrawOverlays(app))
        val settings = FloatingEntrySettings(app)
        val previousSize = settings.sizeDp
        val automation = instrumentation.uiAutomation
        val previousInfo = automation.serviceInfo
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        compose.waitUntil(15000) { compose.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("菜单").performClick()
        compose.onNodeWithText("悬浮按钮").performClick()
        fun awaitWidth(size: Int) {
            val expected = (size * app.resources.displayMetrics.density).roundToInt()
            compose.waitUntil(15000) {
                automation.windows.any { window ->
                    val root = window.root
                    if (root?.contentDescription?.toString() == "返回 ResourceTree 首页并读取剪贴板") {
                        val bounds = Rect(); window.getBoundsInScreen(bounds)
                        bounds.width() == expected && bounds.height() == expected
                    } else false
                }
            }
        }
        try {
            compose.runOnIdle { ContextCompat.startForegroundService(app, Intent(app, FloatingEntryService::class.java)) }
            awaitWidth(previousSize)
            compose.onNodeWithContentDescription("悬浮按钮大小").performSemanticsAction(SemanticsActions.SetProgress) { it(84f) }
            awaitWidth(84)
            assertEquals(84, FloatingEntrySettings(app).sizeDp)
            compose.onNodeWithText("关闭悬浮按钮", substring = false).performClick()
            compose.waitUntil(10000) { !FloatingEntryService.running.value }
            compose.runOnIdle { ContextCompat.startForegroundService(app, Intent(app, FloatingEntryService::class.java)) }
            awaitWidth(84)
            compose.onNodeWithText("恢复默认大小").performClick()
            awaitWidth(56)
        } finally {
            compose.runOnIdle {
                app.stopService(Intent(app, FloatingEntryService::class.java))
                settings.setSize(previousSize.toFloat())
            }
            automation.serviceInfo = previousInfo
        }
    }
}
