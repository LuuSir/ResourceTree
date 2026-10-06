package com.example.resouretree

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.resouretree.overlay.FloatingEntryService
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GraphFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(description: String) {
        try { compose.waitUntil(15000) {
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        } } catch (e: AssertionError) { throw AssertionError("Missing $description\n${compose.onRoot().printToString()}", e) }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun titleSwitchFolderNavigationExpansionSearchAndRecreation() {
        waitFor("切换到图视角")
        compose.onNodeWithContentDescription("切换到图视角").performClick()
        waitFor("文件夹：哔哩哔哩")
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/icon-preview/graph-home.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("文件夹：示例").assertDoesNotExist()
        compose.onNodeWithContentDescription("文件夹：哔哩哔哩").performClick()
        waitFor("文件夹：示例")
        compose.onNodeWithContentDescription("当前目录：哔哩哔哩").assertExists()
        compose.onNodeWithContentDescription("展开全部").performClick()
        compose.onNodeWithContentDescription("展开全部").assertIsOn()
        compose.activityRule.scenario.recreate()
        waitFor("切换到列表视角")
        compose.onNodeWithContentDescription("展开全部").assertIsOn()
        compose.onNodeWithText("搜索", substring = false).performClick()
        compose.onNodeWithText("搜索名称、内容、标签").performTextInput("Bilibili")
        compose.onNodeWithTag("graph-view").assertDoesNotExist()
        compose.onNodeWithText("返回").performClick()
        waitFor("切换到列表视角")
        compose.onNodeWithContentDescription("切换到列表视角").performClick()
        compose.onNodeWithTag("graph-view").assertDoesNotExist()
        compose.onNodeWithText("示例").assertExists()
        compose.onNodeWithText("＋ 新建").assertExists()
    }
    @Test fun floatingHomeResetsGraphAndRootBackReturnsToList() {
        waitFor("切换到图视角")
        compose.onNodeWithContentDescription("切换到图视角").performClick()
        waitFor("文件夹：哔哩哔哩")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("切换到图视角")
        compose.onNodeWithTag("graph-view").assertDoesNotExist()
        compose.onNodeWithContentDescription("切换到图视角").performClick()
        waitFor("文件夹：哔哩哔哩")
        compose.onNodeWithContentDescription("文件夹：哔哩哔哩").performClick()
        waitFor("文件夹：示例")
        compose.runOnIdle { compose.activity.onNewIntent(FloatingEntryService.homeIntent(compose.activity)) }
        waitFor("切换到图视角")
        compose.onNodeWithTag("graph-view").assertDoesNotExist()
        compose.onNodeWithText("哔哩哔哩").assertExists()
        compose.onNodeWithText("返回").assertDoesNotExist()
    }
    @Test fun graphItemExecutesActionWithContentFromRepository() {
        waitFor("切换到图视角")
        val app = compose.activity.application as ResourceTreeApplication
        runBlocking { app.repository.save(ResourceNode("graph-copy", null, NodeType.ITEM, "图中复制",
            content = ResourceContent(text = "来自 Content 的文字"), action = ResourceAction(ActionType.COPY)), true) }
        compose.onNodeWithContentDescription("切换到图视角").performClick()
        waitFor("条目：图中复制")
        compose.onNodeWithContentDescription("条目：图中复制").performTouchInput { click() }
        compose.runOnIdle { assertEquals("来自 Content 的文字", compose.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()) }
    }
}
