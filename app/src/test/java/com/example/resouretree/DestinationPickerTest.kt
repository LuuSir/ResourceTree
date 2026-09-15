package com.example.resouretree

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.components.DestinationPicker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DestinationPickerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun widthStaysFixedWhileScrollingLongNamesAndNavigatingBreadcrumbs() {
        val roots = (0..25).map { ResourceNode("f$it", null, NodeType.FOLDER, if (it == 25) "很长的收藏夹名称".repeat(15) else "文件夹$it", sortOrder = it) }
        val nodes = roots + ResourceNode("child", "f25", NodeType.FOLDER, "收藏夹1") + ResourceNode("grandchild", "child", NodeType.FOLDER, "收藏夹2")
        var selected = "unset"
        compose.setContent { MaterialTheme { DestinationPicker(nodes, null, emptySet(), true, 2, false, { selected = it ?: "root" }, {}) } }
        fun width() = compose.onNodeWithTag("destination-dialog").fetchSemanticsNode().boundsInRoot.width
        val initialWidth = width()
        compose.onNodeWithTag("destination-folder-child").assertDoesNotExist()
        compose.onNodeWithTag("destination-folders").performScrollToNode(hasTestTag("destination-folder-f25"))
        assertEquals(initialWidth, width(), .1f)
        compose.onNodeWithTag("destination-folder-f25").performClick()
        assertEquals(initialWidth, width(), .1f)
        compose.onNodeWithTag("destination-folder-child").performClick()
        compose.onNodeWithTag("destination-folder-grandchild").performClick()
        assertEquals("unset", selected)
        compose.onNode(hasText("收藏夹1") and hasAnyAncestor(hasTestTag("destination-breadcrumb"))).performClick()
        compose.onNodeWithTag("destination-folder-grandchild").assertExists()
        compose.onNodeWithText("复制到此处").performClick()
        compose.runOnIdle { assertEquals("child", selected) }
        assertEquals(initialWidth, width(), .1f)
    }
    @Test fun startsAtCurrentFolderAndMoveExclusionsCannotBeEntered() {
        val root = ResourceNode("root", null, NodeType.FOLDER, "当前位置")
        val excluded = ResourceNode("blocked", "root", NodeType.FOLDER, "选中目录")
        var selected: String? = null
        compose.setContent { MaterialTheme { DestinationPicker(listOf(root, excluded), root.id, setOf(excluded.id), false, 1, false, { selected = it }, {}) } }
        compose.onNodeWithText("当前位置").assertExists()
        compose.onNodeWithTag("destination-folder-blocked").assertDoesNotExist()
        compose.onNodeWithText("移动到此处").performClick()
        compose.runOnIdle { assertEquals(root.id, selected) }
        compose.onNodeWithText("上一级").performClick()
        compose.onNodeWithTag("destination-folder-root").assertExists()
    }
}
