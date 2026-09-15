package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.example.resouretree.data.apps.*
import com.example.resouretree.data.clipboard.ClipboardRuleStore
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.screens.*
import com.example.resouretree.ui.viewmodel.EditorViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardRulesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editRuleSaveThenDisableUsingExistingMenuPage() {
        val store = ClipboardRuleStore(RuntimeEnvironment.getApplication())
        compose.setContent { MaterialTheme { ClipboardRulesScreen(store) {} } }
        compose.onNodeWithTag("rule-edit-bilibili").performClick()
        compose.onNodeWithText("默认名称（可选）").performTextReplacement("我的视频")
        compose.onNodeWithText("目标包名 / 候选包名 *").performTextReplacement("com.bilibili.app.in\ntv.danmaku.bili")
        compose.onNodeWithText("保存规则").performClick()
        assertEquals("我的视频", store.rules.value.first().name)
        assertEquals("com.bilibili.app.in", store.rules.value.first().targets.first())
        compose.onNodeWithTag("rule-enabled-bilibili").performClick()
        assertFalse(store.rules.value.first().enabled)
    }

    @Test fun editorReadsOnlyBoundAppUntilPickerExplicitlyOpened() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ResourceDatabase::class.java).build()
        var scans = 0
        val lookups = mutableListOf<String>()
        val selected = InstalledApp("已绑定应用", "com.test.bound")
        val catalog = object : AppCatalog {
            override suspend fun load(): List<InstalledApp> { scans++; return listOf(selected) }
            override suspend fun find(packageName: String): InstalledApp? { lookups += packageName; return selected }
        }
        lateinit var vm: EditorViewModel
        try {
            compose.setContent {
                vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    EditorViewModel(NodeRepository(db), SavedStateHandle(mapOf("target" to selected.packageName, "actionType" to "COPY_AND_LAUNCH")),
                        null, null, NodeType.ITEM, catalog, DocumentStore(context.contentResolver, java.io.File(context.filesDir, "media")))
                }
                MaterialTheme { EditorScreen(vm, false) {} }
            }
            compose.waitUntil(10000) { compose.waitForIdle(); !vm.state.value.loading && vm.state.value.targetApp != null }
            assertEquals(0, scans); assertEquals(listOf(selected.packageName), lookups)
            compose.onNodeWithText("已绑定应用").performScrollTo().performClick()
            compose.onNodeWithText("搜索应用").assertExists()
            compose.onNodeWithTag("app-com.test.bound").performClick()
            assertEquals(1, scans)
            assertEquals(selected.packageName, vm.state.value.target)
        } finally { db.close() }
    }
}
