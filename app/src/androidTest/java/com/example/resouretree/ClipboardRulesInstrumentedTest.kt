package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.clipboard.ClipboardRuleStore
import com.example.resouretree.domain.model.ClipboardDraftParser
import com.example.resouretree.ui.screens.ClipboardRulesScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardRulesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun customRulePersistsAndResolvesInstalledCandidateWithoutTouchingTree() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ResourceTreeApplication
        val before = runBlocking { app.repository.all.first() }.associateBy { it.id }
        val prefix = "RULE-TEST-${java.util.UUID.randomUUID()}"
        try {
            compose.setContent { MaterialTheme { ClipboardRulesScreen(app.clipboardRules) {} } }
            compose.onNodeWithText("添加规则").performClick()
            compose.onNodeWithText("匹配前缀 *").performTextInput(prefix)
            compose.onNodeWithText("默认名称（可选）").performTextInput("规则验证")
            compose.onNodeWithText("目标包名 / 候选包名 *").performTextInput("com.resourcetree.missing\n${app.packageName}")
            compose.onNodeWithText("保存规则").performClick()
            val rule = ClipboardRuleStore(app).rules.value.single { it.prefix == prefix }
            val draft = requireNotNull(ClipboardDraftParser.parse("$prefix 完整内容", "test", listOf(rule)))
            assertEquals("规则验证", draft.name)
            assertEquals(app.packageName, runBlocking { app.appCatalog.resolveTarget(draft.targets) })
            assertNotNull(runBlocking { app.appCatalog.find(app.packageName) }?.icon)
            assertEquals(before, runBlocking { app.repository.all.first() }.associateBy { it.id })
        } finally {
            compose.runOnIdle { app.clipboardRules.rules.value.filter { it.prefix == prefix }.forEach { app.clipboardRules.delete(it.id) } }
        }
    }
}
