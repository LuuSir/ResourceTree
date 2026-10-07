package com.example.resouretree

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.components.ResourceGraphView
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GraphGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun panAndPinchDoNotExecuteItemsAndTransformedTapDoes() {
        val item = ResourceNode("text", null, NodeType.ITEM, "随手收集", content = ResourceContent(text = "hello"), action = ResourceAction(ActionType.COPY))
        var clicked: ResourceNode? = null
        compose.setContent { MaterialTheme { ResourceGraphView(listOf(item), null, false, true) { clicked = it } } }
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithTag("graph-node-text").fetchSemanticsNodes().isNotEmpty() }
        val canvas = compose.onNodeWithTag("graph-canvas")
        canvas.performTouchInput { swipe(center, center + Offset(30f, -30f)) }
        compose.runOnIdle { assertNull(clicked) }
        canvas.performTouchInput { pinch(center - Offset(30f, 0f), center + Offset(30f, 0f), center - Offset(45f, 0f), center + Offset(45f, 0f)) }
        compose.runOnIdle { assertNull(clicked) }
        compose.onNodeWithTag("graph-node-text").performTouchInput { click() }
        compose.runOnIdle { assertEquals(item, clicked); clicked = null }
        compose.onNodeWithText("适应画布").performClick()
        compose.onNodeWithTag("graph-root").performTouchInput { click() }
        compose.runOnIdle { assertNull(clicked) }
        compose.onNodeWithTag("graph-node-text").assertExists()
    }
    @Test fun renderWarmRadialTreePreview() {
        val names = listOf("哔哩哔哩", "收藏的网页", "灵感素材", "工作资料", "稍后观看", "随手记")
        val nodes = names.mapIndexed { i, name -> ResourceNode("n$i", null, if (i == 5) NodeType.ITEM else NodeType.FOLDER, name, sortOrder = i) }
        compose.setContent { MaterialTheme { ResourceGraphView(nodes, null, false, true) {} } }
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithTag("graph-root").fetchSemanticsNodes().isNotEmpty() }
        val output = File("build/icon-preview/graph-preview.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("6 个节点").assertExists()
    }
    @Test fun largeExpandedGraphRemainsInteractiveWithoutCreatingThousandsOfSemanticsNodes() {
        val nodes = (0 until 10000).map { ResourceNode("n$it", null, NodeType.ITEM, "节点 $it", sortOrder = it) }
        compose.setContent { MaterialTheme { ResourceGraphView(nodes, null, true, true) {} } }
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithText("10000 个节点").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("放大").performClick()
        compose.onNodeWithTag("graph-canvas").performTouchInput { swipe(center, center + Offset(24f, 10f)) }
        compose.onNodeWithText("适应画布").performClick()
        compose.onNodeWithText("10000 个节点").assertExists()
        assertTrue(compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size < 100)
    }

    @Test fun navigationRetainsCanvasWithoutLoadingFlashAndInterruptedRootKeepsItsScreenPosition() {
        val nodes = listOf(ResourceNode("folder", null, NodeType.FOLDER, "目录"),
            ResourceNode("other", null, NodeType.FOLDER, "其它"),
            ResourceNode("child", "folder", NodeType.ITEM, "子条目"))
        val current = mutableStateOf<String?>(null)
        compose.setContent { MaterialTheme { ResourceGraphView(nodes, current.value, false, true) {} } }
        compose.waitUntil(15000) { compose.waitForIdle(); compose.onAllNodesWithTag("graph-node-folder").fetchSemanticsNodes().isNotEmpty() }
        val canvasId = compose.onNodeWithTag("graph-canvas").fetchSemanticsNode().id
        val original = compose.onNodeWithTag("graph-node-folder").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { current.value = "folder" }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(15000) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onAllNodesWithContentDescription("当前目录：目录").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("graph-loading").assertDoesNotExist()
        assertEquals(canvasId, compose.onNodeWithTag("graph-canvas").fetchSemanticsNode().id)
        val initial = compose.onNodeWithTag("graph-root").fetchSemanticsNode().boundsInRoot.center
        assertEquals(original.x, initial.x, 2f); assertEquals(original.y, initial.y, 2f)
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        val middle = compose.onNodeWithTag("graph-root").fetchSemanticsNode().boundsInRoot.center
        assertTrue((middle - initial).getDistance() > 1f)
        compose.runOnIdle { current.value = null }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(15000) {
            compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
            compose.onAllNodesWithContentDescription("当前目录：首页").fetchSemanticsNodes().isNotEmpty()
        }
        val resumed = compose.onNodeWithTag("graph-node-folder").fetchSemanticsNode().boundsInRoot.center
        // One frame may pass while async layout finishes; it must not snap back to either endpoint.
        assertTrue((resumed - middle).getDistance() < 25f)
        compose.onNodeWithTag("graph-loading").assertDoesNotExist()
        assertEquals(canvasId, compose.onNodeWithTag("graph-canvas").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("当前目录：首页").assertExists()
        compose.onNodeWithContentDescription("条目：子条目").assertDoesNotExist()
    }
}
