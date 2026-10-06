package com.example.resouretree

import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.graph.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class ResourceGraphLayoutTest {
    private fun folder(id: String, parent: String? = null, order: Int = 0, pinned: Boolean = false) =
        ResourceNode(id, parent, NodeType.FOLDER, "同名目录", sortOrder = order, createdAt = 0, isPinned = pinned)
    private val nodes = listOf(folder("a"), folder("b", order = 1), folder("a1", "a"), folder("a2", "a"), folder("deep", "a1"),
        ResourceNode("item", "deep", NodeType.ITEM, "很长的条目名称".repeat(20)))

    @Test fun oneLevelAndExpandedAreScopedToCurrentFolder() {
        val root = ResourceGraphLayout.build(nodes, null, false)
        assertNull(root.nodes.first().resource)
        assertEquals(listOf(null, "a", "b"), root.nodes.map { it.resource?.id })
        assertEquals(listOf("a", "a1", "a2"), ResourceGraphLayout.build(nodes, "a", false).nodes.map { it.resource?.id })
        val expanded = ResourceGraphLayout.build(nodes, "a", true)
        assertEquals(setOf("a", "a1", "a2", "deep", "item"), expanded.nodes.map { it.resource?.id }.toSet())
        assertEquals(nodes.size + 1, ResourceGraphLayout.build(nodes, null, true).nodes.size)
        for (node in expanded.nodes.drop(1)) assertEquals(node.resource?.parentId, expanded.nodes[node.parent!!].resource?.id)
    }
    @Test fun pinningOrderingAndCoordinatesAreStableAcrossInputOrder() {
        val source = listOf(folder("a", order = 0), folder("b", order = 4, pinned = true), folder("c", order = 1))
        val graph = ResourceGraphLayout.build(source, null, true)
        assertEquals(listOf(null, "b", "a", "c"), graph.nodes.map { it.resource?.id })
        assertEquals(graph, ResourceGraphLayout.build(source.reversed(), null, true))
    }
    @Test fun emptyFolderIsStillACentredNode() {
        val graph = ResourceGraphLayout.build(nodes, "a2", true)
        assertEquals(1, graph.nodes.size)
        assertEquals(GraphPoint(0f, 0f), graph.nodes.single().position)
        assertEquals("a2", graph.hit(GraphPoint(0f, 0f))?.resource?.id)
        assertNull(graph.hit(GraphPoint(100f, 100f)))
    }
    @Test(timeout = 10000) fun tenThousandNodesAreNotTruncatedAndNeighbourCardsDoNotOverlap() {
        val graph = ResourceGraphLayout.build((0 until 10000).map { folder("n$it", order = it) }, null, true)
        assertEquals(10001, graph.nodes.size)
        for (i in 2 until graph.nodes.size) {
            val a = graph.nodes[i - 1].position; val b = graph.nodes[i].position
            assertTrue(hypot(a.x - b.x, a.y - b.y) > 170f)
        }
        val fitted = GraphCamera.fit(graph.bounds, 360f, 600f)
        assertTrue(fitted.scale > 0f)
        assertTrue(graph.bounds.width * fitted.scale <= 361f)
    }
    @Test fun deepHierarchyAndUnevenSubtreesHaveFiniteNonOverlappingPositions() {
        val deep = (0 until 100).map { folder("d$it", if (it == 0) null else "d${it - 1}") }
        val graph = ResourceGraphLayout.build(deep + (0 until 80).map { folder("b$it", "d0") }, null, true)
        assertEquals(181, graph.nodes.size)
        for (i in graph.nodes.indices) for (j in i + 1 until graph.nodes.size) {
            val a = graph.nodes[i].position; val b = graph.nodes[j].position
            assertTrue(a.x.isFinite() && a.y.isFinite())
            assertTrue(hypot(a.x - b.x, a.y - b.y) > 170f)
        }
    }
    @Test fun zoomKeepsPivotAndHitTestingUsesInverseTransform() {
        val camera = GraphCamera(.7f, 12f, -18f)
        val pivot = GraphPoint(50f, 60f)
        val before = camera.world(pivot)
        val zoomed = camera.transform(pivot, GraphPoint(0f, 0f), 2f, .01f)
        val after = zoomed.world(pivot)
        assertEquals(before.x, after.x, .001f); assertEquals(before.y, after.y, .001f)
        val graph = ResourceGraphLayout.build(nodes, null, false)
        val node = graph.nodes[1]
        assertEquals(node, graph.hit(zoomed.world(zoomed.screen(node.position))))
        assertEquals(3f, zoomed.transform(pivot, GraphPoint(0f, 0f), 100f, .01f).scale)
    }
}
