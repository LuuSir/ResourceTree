package com.example.resouretree

import android.content.ClipData
import com.example.resouretree.data.apps.AppCatalog
import com.example.resouretree.data.apps.InstalledApp
import com.example.resouretree.data.clipboard.*
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardRulesTest {
    @Test fun legacyRulesKeepCopyActionAndWebRulePersistsWithoutTarget() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("clipboard-rules", 0).edit().putString("rules", """[{"id":"old","prefix":"BV","targets":["com.bilibili.app.in"],"enabled":true}]""").commit()
        val store = ClipboardRuleStore(context)
        assertEquals(ActionType.COPY_AND_LAUNCH, store.rules.value.single().actionType)
        assertEquals(ClipboardMatchType.PREFIX, store.rules.value.single().matchType)
        store.save(ClipboardRule("web", "https://", targets = emptyList(), actionType = ActionType.OPEN_WEBVIEW))
        assertEquals(ActionType.OPEN_WEBVIEW, ClipboardRuleStore(context).rules.value.last().actionType)
        assertTrue(ClipboardRuleStore(context).rules.value.last().targets.isEmpty())
    }
    @Test fun wildcardModePersistsAndOnlySameModeDuplicatesAreRejected() {
        val context = RuntimeEnvironment.getApplication()
        val store = ClipboardRuleStore(context)
        val rule = ClipboardRule("glob", "BV", targets = listOf("com.test.app"), matchType = ClipboardMatchType.WILDCARD)
        store.save(rule)
        assertEquals(rule, ClipboardRuleStore(context).rules.value.last())
        assertThrows(IllegalArgumentException::class.java) { store.save(rule.copy(id = "duplicate")) }
    }
    @Test fun longestEnabledPrefixPreservesTextAndConfigurableName() {
        val rules = listOf(ClipboardRule("1", "B", "通用", listOf("com.test.one")),
            ClipboardRule("2", "BV", targets = listOf("com.test.two")))
        val text = "  BV123\n完整文字"
        val draft = requireNotNull(ClipboardDraftParser.parse(text, "token", rules))
        assertEquals(text, draft.text); assertEquals("BV123", draft.name)
        assertEquals(listOf("com.test.two"), draft.targets)
        assertEquals("通用", ClipboardDraftParser.parse(text, "token", rules.map { if (it.id == "2") it.copy(enabled = false) else it })!!.name)
        assertNull(ClipboardDraftParser.parse("bv123", "token", rules))
    }

    @Test fun editsDisablesAndDeletionPersistWithoutRestoringDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val store = ClipboardRuleStore(context)
        assertEquals(ClipboardRule.defaults, store.rules.value)
        val inbox = ClipboardDraftInbox(context, store)
        val edited = ClipboardRule.defaults[0].copy(prefix = "NEW", name = "自定义", targets = listOf("com.test.bound"))
        store.save(edited)
        inbox.offer(ClipData.newPlainText("external", "BV123")); assertNull(inbox.pending.value)
        inbox.offer(ClipData.newPlainText("external", "NEW text")); assertEquals("自定义", inbox.pending.value?.name)
        store.save(edited.copy(enabled = false))
        assertFalse(ClipboardRuleStore(context).rules.value.first().enabled)
        inbox.offer(ClipData.newPlainText("external", "NEW text")); assertNull(inbox.pending.value)
        store.rules.value.forEach { store.delete(it.id) }
        assertTrue(ClipboardRuleStore(context).rules.value.isEmpty())
    }

    @Test fun validatesPackagesNormalizesDuplicatesAndRejectsDuplicatePrefixes() {
        val rule = ClipboardRule("custom", " ABC ", targets = listOf(" com.test.one ", "", "com.test.one", "com.test.two"))
        assertEquals(listOf("com.test.one", "com.test.two"), rule.validated().targets)
        listOf(rule.copy(prefix = " "), rule.copy(targets = emptyList()), rule.copy(targets = listOf("bad package"))).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validated() }
        }
        val store = ClipboardRuleStore(RuntimeEnvironment.getApplication())
        assertThrows(IllegalArgumentException::class.java) { store.save(rule.copy(prefix = "BV")) }
    }

    @Test fun resolvingCandidatesStopsAtFirstAvailableWithoutLoadingCatalog() = runBlocking {
        val queried = mutableListOf<String>()
        val catalog = object : AppCatalog {
            override suspend fun load(): List<InstalledApp> = error("Must not scan all apps")
            override suspend fun find(packageName: String): InstalledApp? {
                queried += packageName
                return if (packageName == "com.test.available") InstalledApp("应用", packageName) else null
            }
        }
        assertEquals("com.test.available", catalog.resolveTarget(listOf("com.test.missing", "com.test.available", "com.test.other")))
        assertEquals(listOf("com.test.missing", "com.test.available"), queried)
        assertEquals("com.test.missing", catalog.resolveTarget(listOf("com.test.missing")))
    }
}
