package com.example.resouretree

import android.content.ClipData
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.resouretree.domain.model.*
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
class ClipboardImportFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as ResourceTreeApplication
    private fun waitFor(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun bvDraftSurvivesRecreationAndOnlySavesToRootAfterConfirmation() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("哔哩哔哩").performClick()
        val text = "BV1meMS6rE6Z\n完整备注"
        val clip = ClipData.newPlainText("外部应用", text)
        compose.runOnIdle { app.clipboardDrafts.offer(clip) }
        waitFor("新建条目")
        compose.onNodeWithText("文本内容").assertTextContains(text)
        runBlocking { assertFalse(app.repository.all.first().any { it.content.text == text }) }
        compose.onNodeWithText("名称 *").performTextReplacement("确认后的草稿")
        compose.activityRule.scenario.recreate()
        waitFor("新建条目")
        compose.onNodeWithText("名称 *").assertTextContains("确认后的草稿")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("确认后的草稿")
        val saved = runBlocking { app.repository.all.first().single { it.name == "确认后的草稿" } }
        assertNull(saved.parentId); assertEquals(text, saved.content.text)
        assertEquals(ContentType.TEXT, saved.content.type)
        assertEquals(ActionType.COPY_AND_LAUNCH, saved.action.type)
        assertTrue(saved.action.target in listOf("tv.danmaku.bili", "com.bilibili.app.in"))
        compose.runOnIdle { app.clipboardDrafts.offer(clip) }
        compose.onNodeWithText("新建条目").assertDoesNotExist()
    }
    @Test fun taobaoWaitsForExistingEditorAndCanBeSavedWithoutChoosingTargetManually() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("＋ 新建").performClick()
        compose.onNodeWithText("新建条目").performClick()
        compose.onNodeWithText("名称 *").performTextInput("未完成的编辑")
        val text = "【淘宝】商品链接 https://example.test/abc"
        compose.runOnIdle { app.clipboardDrafts.offer(ClipData.newPlainText("外部应用", text)) }
        compose.onNodeWithText("名称 *").assertTextContains("未完成的编辑")
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("放弃", substring = false).performClick()
        waitFor("淘宝分享")
        compose.onNodeWithText("文本内容").assertTextContains(text)
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("ResourceTree")
        val saved = runBlocking { app.repository.all.first().single { it.name == "淘宝分享" } }
        assertNull(saved.parentId); assertEquals(text, saved.content.text)
        assertEquals(ResourceAction(ActionType.COPY_AND_LAUNCH, "com.taobao.taobao"), saved.action)
    }

    @Test fun changedRulesApplyToPendingClipboardAfterLeavingRuleEditor() {
        waitFor("哔哩哔哩")
        compose.onNodeWithText("菜单").performClick()
        compose.onNodeWithText("剪贴板规则").performClick()
        compose.onNodeWithTag("rule-edit-bilibili").performClick()
        compose.onNodeWithText("默认名称（可选）").performTextReplacement("自定预填")
        compose.onNodeWithText("目标包名 / 候选包名 *").performTextReplacement("com.test.direct")
        compose.runOnIdle { app.clipboardDrafts.offer(ClipData.newPlainText("外部应用", "BV_RULE_EDIT")) }
        compose.onNodeWithText("编辑剪贴板规则").assertExists()
        compose.onNodeWithText("新建条目").assertDoesNotExist()
        compose.onNodeWithText("保存规则").performClick()
        compose.onNodeWithText("返回").performClick()
        waitFor("新建条目")
        compose.onNodeWithText("名称 *").assertTextContains("自定预填")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitFor("自定预填")
        val saved = runBlocking { app.repository.all.first().single { it.name == "自定预填" } }
        assertEquals("com.test.direct", saved.action.target)
        assertEquals("BV_RULE_EDIT", saved.content.text)
        assertNull(saved.parentId)
    }
}
