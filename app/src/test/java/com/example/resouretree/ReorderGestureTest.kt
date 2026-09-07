package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.components.ReorderableNodeList
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReorderGestureTest {
    @get:Rule val compose = createComposeRule()
    @Test fun longDragCommitsOrderWithoutClickOrMenu() {
        val nodes = (1..3).map { ResourceNode("$it", null, NodeType.ITEM, "条目$it") }
        val current = mutableStateOf(nodes)
        var committed = emptyList<String>()
        var clicks = 0
        compose.setContent { MaterialTheme {
            ReorderableNodeList(current.value, false, emptySet(), false, true,
                onClick = { clicks++ }, onMenu = { clicks++ }, onReorder = { ids ->
                    committed = ids; current.value = ids.map { id -> nodes.first { it.id == id } }
                })
        } }
        val list = compose.onNodeWithTag("resource-list").fetchSemanticsNode().boundsInRoot
        val from = compose.onNodeWithTag("node-3").fetchSemanticsNode().boundsInRoot.center.y - list.top
        val to = compose.onNodeWithTag("node-1").fetchSemanticsNode().boundsInRoot.center.y - list.top
        compose.onNodeWithTag("resource-list").performTouchInput {
            down(Offset(40f, from))
            moveTo(Offset(40f, from), delayMillis = 800)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithTag("resource-list").performTouchInput {
            moveTo(Offset(40f, to), delayMillis = 600)
            up()
        }
        compose.runOnIdle { assertEquals(listOf("3", "1", "2"), committed); assertEquals(0, clicks) }
    }
}
