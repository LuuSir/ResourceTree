package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.action.ActionExecutor
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.screens.BrowserScreen
import com.example.resouretree.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Real-device UI verification uses only an in-memory database; existing user resources are untouched. */
@RunWith(AndroidJUnit4::class)
class BatchSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: ResourceDatabase
    private lateinit var repository: NodeRepository
    private lateinit var vm: BrowserViewModel
    private lateinit var destination: ResourceNode
    private var executions = 0

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ResourceDatabase::class.java).build()
        repository = NodeRepository(db)
        destination = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "测试目标")
        runBlocking {
            listOf(destination,
                ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "测试甲", action = ResourceAction(ActionType.COPY, "甲")),
                ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "测试乙", action = ResourceAction(ActionType.COPY, "乙")))
                .forEach { repository.save(it, true) }
        }
        compose.setContent {
            vm = androidx.lifecycle.viewmodel.compose.viewModel {
                BrowserViewModel(repository, DocumentStore(context.contentResolver),
                    ActionExecutor({ executions++ }, { executions++; true }), SavedStateHandle())
            }
            MaterialTheme { BrowserScreen(vm, onCreate = { _, _ -> }, onEdit = {}) }
        }
        waitFor("测试甲")
    }

    @After fun close() { if (::db.isInitialized) db.close() }
    private fun waitFor(text: String) = compose.waitUntil(15000) {
        compose.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()
    }
    private fun chooseBoth() {
        compose.onNodeWithContentDescription("更多：测试甲").performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("已选 1 项").assertExists()
        compose.onNodeWithText("测试乙").performClick()
        compose.onNodeWithText("已选 2 项").assertExists()
        compose.runOnIdle { assertEquals(0, executions) }
    }

    @Test fun batchCopyMoveAndDeleteThroughMoreSelection() {
        chooseBoth()
        compose.onNodeWithText("复制", substring = false).performClick()
        compose.onNodeWithText("复制 2 项到…").assertExists()
        compose.onNodeWithText("首页 / 测试目标").performClick()
        waitFor("ResourceTree")
        runBlocking { assertEquals(2, repository.children(destination.id).first().size) }
        chooseBoth()
        compose.onNodeWithText("移动", substring = false).performClick()
        compose.onNodeWithText("首页 / 测试目标").performClick()
        waitFor("ResourceTree")
        compose.waitUntil(10000) { compose.onAllNodesWithText("测试甲").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("测试目标").performClick()
        waitFor("测试甲")
        compose.onAllNodesWithContentDescription("更多：测试甲")[0].performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("全选", substring = false).performClick()
        compose.onNodeWithText("已选 4 项").assertExists()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNodeWithText("删除所选 4 项？").assertExists()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        runBlocking { assertEquals(4, repository.children(destination.id).first().size) }
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.onNode(hasText("删除") and hasAnyAncestor(isDialog())).performClick()
        waitFor("这里还没有内容\n点击 + 创建文件夹或条目")
        runBlocking { assertTrue(repository.children(destination.id).first().isEmpty()) }
        compose.runOnIdle { assertEquals(0, executions) }
    }

    @Test fun selectAllAndCancelDoNotExecuteItems() {
        chooseBoth()
        compose.onNodeWithText("全选", substring = false).performClick()
        compose.onNodeWithText("已选 3 项").assertExists()
        compose.onNodeWithText("取消全选").performClick()
        compose.onNodeWithText("已选 0 项").assertExists()
        compose.onNodeWithText("复制", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("取消", substring = false).performClick()
        waitFor("ResourceTree")
        compose.runOnIdle { assertEquals(0, executions) }
    }

    @Test fun longPressDragsAndMorePinsWithoutExecutingAction() {
        val before = runBlocking { repository.children(null).first() }
        val first = before.first()
        val last = before.last()
        val from = compose.onNodeWithTag("node-${last.id}").fetchSemanticsNode().boundsInRoot.center
        val to = compose.onNodeWithTag("node-${first.id}").fetchSemanticsNode().boundsInRoot.center
        val listBounds = compose.onNodeWithTag("resource-list").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("resource-list").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(40f, from.y - listBounds.top))
            moveTo(androidx.compose.ui.geometry.Offset(40f, from.y - listBounds.top), delayMillis = 800)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithTag("resource-list").performTouchInput {
            moveTo(androidx.compose.ui.geometry.Offset(40f, to.y - listBounds.top), delayMillis = 700)
            advanceEventTime(100)
            up()
        }
        compose.waitUntil(10000) { runBlocking { repository.children(null).first().first().id == last.id } }
        compose.onNodeWithText("多选", substring = false).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, executions) }
        compose.onNodeWithContentDescription("更多：${first.name}").performClick()
        compose.onNodeWithText("置顶", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { repository.children(null).first().first().isPinned } }
        runBlocking { assertEquals(first.id, repository.children(null).first().first().id) }
        compose.onNodeWithText("已置顶", substring = false).assertExists()
        compose.onNodeWithContentDescription("更多：${first.name}").performClick()
        compose.onNodeWithText("取消置顶", substring = false).performClick()
        compose.waitUntil(10000) { runBlocking { repository.children(null).first().none { it.isPinned } } }
        compose.runOnIdle { assertEquals(0, executions) }
    }
}
