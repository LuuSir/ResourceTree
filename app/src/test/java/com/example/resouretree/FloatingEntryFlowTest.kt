package com.example.resouretree

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.resouretree.overlay.FloatingEntryService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingEntryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as ResourceTreeApplication
    private fun waitFor(text: String) = compose.waitUntil(15000) {
        compose.waitForIdle()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun returnHome() = compose.runOnIdle {
        compose.activity.onNewIntent(FloatingEntryService.homeIntent(compose.activity))
    }

    @Test fun homeIntentLeavesFolderAndDoesNotRepeatAfterRecreation() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("哔哩哔哩").performClick()
        waitFor("返回")
        returnHome()
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithText("返回").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("哔哩哔哩").performClick()
        waitFor("返回")
        compose.onNodeWithText("＋ 新建").performClick()
        compose.onNodeWithText("新建条目").performClick()
        compose.onNodeWithText("名称 *").performTextInput("旋转后仍保留")
        compose.activityRule.scenario.recreate()
        waitFor("新建条目")
        compose.onNodeWithText("名称 *").assertTextContains("旋转后仍保留")
        compose.onNodeWithText("返回首页？").assertDoesNotExist()
    }

    @Test fun returnHomeProtectsEditThenOpensPendingClipboardAtRoot() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("哔哩哔哩").performClick()
        compose.onNodeWithText("＋ 新建").performClick()
        compose.onNodeWithText("新建条目").performClick()
        compose.onNodeWithText("名称 *").performTextInput("未保存内容")
        compose.runOnIdle {
            compose.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("external", "BV_FLOATING"))
            compose.activity.onWindowFocusChanged(true)
        }
        returnHome()
        waitFor("返回首页？")
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("名称 *").assertTextContains("未保存内容")
        returnHome()
        waitFor("放弃并返回")
        compose.onNodeWithText("放弃并返回").performClick()
        waitFor("BV_FLOATING")
        compose.onNodeWithText("文本内容").assertTextContains("BV_FLOATING")
        compose.onNodeWithText("名称 *").performTextReplacement("悬浮导入")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("悬浮导入")
        val saved = runBlocking { app.repository.all.first().single { it.name == "悬浮导入" } }
        assertNull(saved.parentId)
        assertEquals("BV_FLOATING", saved.content.text)
        assertFalse(runBlocking { app.repository.all.first().any { it.name == "未保存内容" } })
    }

    @Test fun menuExposesUserControlledFloatingEntry() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("菜单").performClick()
        compose.onNodeWithText("悬浮按钮").performClick()
        compose.onNodeWithText("开启悬浮按钮").assertExists()
        returnHome()
        waitFor("ResourceTree")
        compose.onNodeWithText("开启悬浮按钮").assertDoesNotExist()
    }
}
