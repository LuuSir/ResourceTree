package com.example.resouretree

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.resouretree.overlay.FloatingEntrySettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingEntrySizeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun sizeSliderPersistsAndDefaultCanBeRestored() {
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("菜单").performClick()
        compose.onNodeWithText("悬浮按钮").performClick()
        compose.onNodeWithContentDescription("悬浮按钮大小").performSemanticsAction(SemanticsActions.SetProgress) { it(84f) }
        compose.onNodeWithText("悬浮按钮大小：84 dp").assertExists()
        assertEquals(84, FloatingEntrySettings(compose.activity).sizeDp)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithText("悬浮按钮大小：84 dp").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("恢复默认大小").performClick()
        assertEquals(56, FloatingEntrySettings(compose.activity).sizeDp)
        val settings = FloatingEntrySettings(compose.activity)
        settings.preferences.edit().putInt("x", 123).putInt("y", 45).apply()
        assertEquals(36, settings.setSize(-100f))
        assertEquals(96, settings.setSize(1000f))
        assertEquals(56, settings.setSize(55f))
        assertEquals(123, settings.preferences.getInt("x", 0))
        assertEquals(45, settings.preferences.getInt("y", 0))
    }
}
