package com.example.resouretree

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.domain.action.AndroidClipboardWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real foreground clipboard listener without saving or deleting user resources. */
@RunWith(AndroidJUnit4::class)
class ClipboardImportInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun waitFor(text: String) = compose.waitUntil(15000) { compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() }

    @Test fun foregroundBvAndTaobaoClipboardOpenPrefilledDraftsWithoutAutoSaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ResourceTreeApplication
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        compose.setContent { Text("剪贴板验证") }
        var original: ClipData? = null
        val before = runBlocking { app.repository.all.first() }.associateBy { it.id }
        val bv = "BV1meMS6rE6Z\n剪贴板真机验证"
        compose.runOnIdle {
            original = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("ClipboardVerification", bv))
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitFor("新建条目")
            compose.onNodeWithText("文本内容").assertTextContains(bv)
            compose.onNodeWithText("名称 *").assertTextContains("BV1meMS6rE6Z")
            compose.onNodeWithText("复制并打开 App", substring = false).performScrollTo().assertExists()
            compose.onNodeWithText("返回", substring = false).performClick()
            compose.onNodeWithText("放弃", substring = false).performClick()
            waitFor("ResourceTree")
            val taobao = "【淘宝】剪贴板真机验证 https://example.test/item"
            compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("ClipboardVerification", taobao)) }
            waitFor("新建条目")
            compose.onNodeWithText("名称 *").assertTextContains("淘宝分享")
            compose.onNodeWithText("文本内容").assertTextContains(taobao)
            compose.onNodeWithText("返回", substring = false).performClick()
            compose.onNodeWithText("放弃", substring = false).performClick()
            waitFor("ResourceTree")
            compose.runOnIdle { AndroidClipboardWriter(context).write("BV_SELF_COPY_SHOULD_NOT_IMPORT") }
            compose.waitForIdle()
            compose.onNodeWithText("新建条目", substring = false).assertDoesNotExist()
            val after = runBlocking { app.repository.all.first() }.associateBy { it.id }
            assertEquals(before, after)
        } finally {
            scenario?.close()
            // MainActivity is stopped before restoring the user's clipboard, so its listener cannot consume it.
            compose.runOnIdle {
                if (original != null) clipboard.setPrimaryClip(requireNotNull(original))
                else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
