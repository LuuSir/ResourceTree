package com.example.resouretree

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun browseCreateCopySearchEditAndDelete() {
        fun waitFor(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        waitFor("哔哩哔哩")
        compose.onNodeWithText("哔哩哔哩").performClick()
        waitFor("示例")
        compose.onNodeWithText("示例").performClick()
        waitFor("Bilibili 示例")
        compose.onNodeWithText("＋ 新建").performClick()
        compose.onNodeWithText("新建条目").performClick()
        waitFor("名称 *")
        compose.onNodeWithText("名称 *").performTextInput("本地测试条目")
        compose.onNodeWithText("文本内容", substring = false).performTextInput("复制测试 ABC")
        compose.onNodeWithText("标签（逗号分隔）").performTextInput("测试,三国")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("本地测试条目")
        compose.onNodeWithText("本地测试条目").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("复制测试 ABC", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
        compose.onNodeWithText("搜索", substring = false).performClick()
        compose.onNodeWithText("搜索名称、内容、标签").performTextInput("本地测试")
        waitFor("本地测试条目")
        compose.onNodeWithContentDescription("更多：本地测试条目").performClick()
        compose.onNodeWithText("编辑", substring = false).performClick()
        waitFor("名称 *")
        compose.onNodeWithText("名称 *").performTextReplacement("本地测试修改")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("本地测试修改")
        compose.onNodeWithContentDescription("更多：本地测试修改").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("删除“本地测试修改”？").assertExists()
        compose.onNodeWithText("删除", substring = false).performClick()
        waitFor("没有匹配的条目")
    }
}
