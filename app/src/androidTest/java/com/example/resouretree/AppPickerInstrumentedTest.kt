package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.apps.AndroidAppCatalog
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.screens.EditorScreen
import com.example.resouretree.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Uses the real phone application catalog with an isolated in-memory resource database. */
@RunWith(AndroidJUnit4::class)
class AppPickerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: ResourceDatabase
    @After fun close() { if (::db.isInitialized) db.close() }

    @Test fun selectInstalledAppAndSaveWithoutTypingPackageName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = AndroidAppCatalog(context)
        val apps = runBlocking { catalog.load() }
        assertTrue("The phone should expose launchable applications", apps.size > 1)
        assertEquals(apps.size, apps.map { it.packageName }.toSet().size)
        val ownApp = apps.first { it.packageName == context.packageName }
        assertNotNull(ownApp.icon)
        db = Room.inMemoryDatabaseBuilder(context, ResourceDatabase::class.java).build()
        val repository = NodeRepository(db)
        lateinit var vm: EditorViewModel
        var savedBack = false
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel {
                EditorViewModel(repository, SavedStateHandle(), null, null, NodeType.ITEM, catalog, com.example.resouretree.data.transfer.DocumentStore(context.contentResolver, java.io.File(context.filesDir, "media")))
            }
            MaterialTheme { EditorScreen(vm, false) { savedBack = true } }
        }
        compose.waitUntil(15000) { !vm.state.value.loading }
        compose.onNodeWithText("名称 *").performTextInput("应用选择测试")
        compose.onNodeWithText("打开 App", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("选择应用", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("搜索应用").performTextInput(ownApp.name)
        compose.waitUntil(15000) { compose.onAllNodesWithTag("app-${ownApp.packageName}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-${ownApp.packageName}").performClick()
        compose.onNodeWithText("搜索应用").assertDoesNotExist()
        compose.onNodeWithText(ownApp.name).assertExists()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.waitUntil(15000) { savedBack }
        val saved = runBlocking { repository.children(null).first().single() }
        assertEquals(ownApp.packageName, saved.action.target)
        assertEquals(ActionType.LAUNCH_APP, saved.action.type)
    }
}
