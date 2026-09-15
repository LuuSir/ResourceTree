package com.example.resouretree

import android.os.ParcelFileDescriptor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.domain.action.*
import com.example.resouretree.domain.model.ActionType
import com.example.resouretree.ui.screens.BrowserScreen
import com.example.resouretree.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only regression against installed user resources. Never inserts, updates, or deletes data. */
@RunWith(AndroidJUnit4::class)
class LiveDataMigrationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun migratedBilibiliItemStillCopiesContentAndLaunchesOnClick() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as ResourceTreeApplication
        val item = runBlocking { app.repository.all.first() }.firstOrNull {
            it.action.type == ActionType.COPY_AND_LAUNCH && it.action.target == "com.bilibili.app.in" && it.content.text.isNotEmpty()
        }
        assumeNotNull(item)
        val original = requireNotNull(item)
        var copied = ""
        val clipboard = AndroidClipboardWriter(context)
        val executor = ActionExecutor({ copied = it; clipboard.write(it) }, AndroidPackageLauncher(context), AndroidContentSharer(context))
        compose.setContent {
            val vm = androidx.lifecycle.viewmodel.compose.viewModel { BrowserViewModel(app.repository, app.documents, executor, SavedStateHandle()) }
            MaterialTheme { BrowserScreen(vm, { _, _ -> }, {}) }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("搜索", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索", substring = false).performClick()
        compose.onNodeWithText("搜索名称、内容、标签").performTextInput(original.content.text)
        compose.waitUntil(15000) { compose.onAllNodesWithTag("node-${original.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("node-${original.id}").performClick()
        assertEquals(original.content.text, copied)
        compose.waitUntil(10000) {
            val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                .lineSequence().any { it.contains("topResumedActivity") && it.contains(original.action.target) }
        }
    }
}
