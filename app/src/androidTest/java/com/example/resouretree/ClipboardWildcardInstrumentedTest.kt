package com.example.resouretree

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.clipboard.ClipboardRuleStore
import com.example.resouretree.domain.model.ClipboardMatchType
import com.example.resouretree.ui.screens.ClipboardRulesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardWildcardInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun editWildcardRuleThenRealClipboardPrefillsCompleteTextWithoutSaving() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ResourceTreeApplication
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        val before = runBlocking { app.repository.all.first() }.associateBy { it.id }
        val marker = "WILD-${java.util.UUID.randomUUID()}"
        val pattern = "*$marker?*"
        var original: ClipData? = null
        var scenario: ActivityScenario<MainActivity>? = null
        compose.setContent { MaterialTheme { ClipboardRulesScreen(app.clipboardRules) {} } }
        compose.runOnIdle { original = clipboard.primaryClip }
        try {
            compose.onNodeWithText("添加规则").performClick()
            compose.onNodeWithText("通配符匹配", substring = false).performClick()
            compose.onNodeWithText("通配符规则 *").performTextInput(pattern)
            compose.onNodeWithText("默认名称（可选）").performTextInput("通配符验证")
            compose.onNodeWithText("目标包名 / 候选包名 *").performScrollTo().performTextInput(app.packageName)
            compose.onNodeWithText("保存规则").performClick()
            assertEquals(ClipboardMatchType.WILDCARD, ClipboardRuleStore(app).rules.value.single { it.prefix == pattern }.matchType)
            val text = "开头文案\n${marker}😀 后续完整内容"
            compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("WildcardVerification", text)) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15000) { compose.onAllNodesWithText("新建条目").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("名称 *").assertTextContains("通配符验证")
            compose.onNodeWithText("文本内容").assertTextContains(text)
            compose.onNodeWithText("返回", substring = false).performClick()
            compose.onNodeWithText("放弃", substring = false).performClick()
            assertEquals(before, runBlocking { app.repository.all.first() }.associateBy { it.id })
        } finally {
            scenario?.close()
            compose.runOnIdle {
                app.clipboardRules.rules.value.filter { it.prefix == pattern }.forEach { app.clipboardRules.delete(it.id) }
                if (original != null) clipboard.setPrimaryClip(requireNotNull(original))
                else if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
