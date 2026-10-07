package com.example.resouretree

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.components.ResourceGraphView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/** Real-device graph transitions with isolated resources; never opens the user's database. */
@RunWith(AndroidJUnit4::class)
class GraphTransitionInstrumentedTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(90)).around(compose)
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
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Interrupted frame moved from $middle to $resumed (density $density)",
            (resumed - middle).getDistance() < 25f * density)
        compose.onNodeWithTag("graph-loading").assertDoesNotExist()
        assertEquals(canvasId, compose.onNodeWithTag("graph-canvas").fetchSemanticsNode().id)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("当前目录：首页").assertExists()
        compose.onNodeWithContentDescription("条目：子条目").assertDoesNotExist()
    }
}
