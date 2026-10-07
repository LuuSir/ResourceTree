package com.example.resouretree

import com.example.resouretree.domain.model.NodeType
import com.example.resouretree.domain.model.ResourceNode
import com.example.resouretree.ui.graph.*
import org.junit.Assert.*
import org.junit.Test

class GraphTransitionTest {
    private val nodes = listOf(ResourceNode("folder", null, NodeType.FOLDER, "目录"),
        ResourceNode("other", null, NodeType.ITEM, "其它"),
        ResourceNode("child", "folder", NodeType.FOLDER, "子目录"),
        ResourceNode("item", "child", NodeType.ITEM, "内容"))

    @Test fun enteringFolderKeepsSharedNodeAndCameraContinuousAndFadesNewContents() {
        val home = ResourceGraphLayout.build(nodes, null, false)
        val next = ResourceGraphLayout.build(nodes, "folder", false)
        val source = GraphFrame.of(home, GraphCamera(.8f, 30f, -12f))
        val fitted = GraphCamera.fit(next.bounds, 360f, 600f)
        val transition = GraphTransition.between(source, next, fitted)
        val first = transition.frame(0f)
        assertEquals(source.camera, first.camera)
        for (node in source.nodes) {
            val shown = first.nodes.single { it.key == node.key }
            assertEquals(node.position, shown.position)
            assertEquals(node.opacity, shown.opacity, 0f)
            assertEquals(node.rootWeight, shown.rootWeight, 0f)
        }
        assertFalse(first.nodes.any { it.key == "child" })
        val middle = transition.frame(.5f)
        assertEquals(.5f, middle.nodes.single { it.key == "child" }.opacity, 0f)
        assertEquals(.5f, middle.nodes.single { it.key == "folder" }.rootWeight, 0f)
        assertNull(first.hit(first.nodes.single { it.key == "folder" }.position)) // It is now the centre.
        assertNull(first.hit(first.nodes.single { it.key == "other" }.position)) // It is leaving.
        assertEquals(GraphFrame.of(next, fitted), transition.frame(1f))
    }

    @Test fun interruptedNavigationStartsFromTheActualDisplayedFrameIncludingFadesAndCameraGesture() {
        val home = ResourceGraphLayout.build(nodes, null, true)
        val folder = ResourceGraphLayout.build(nodes, "folder", false)
        val source = GraphFrame.of(home, GraphCamera.fit(home.bounds, 360f, 600f))
        val transition = GraphTransition.between(source, folder, GraphCamera.fit(folder.bounds, 360f, 600f))
        val panned = GraphCamera(1.2f, 71f, -40f)
        val interrupted = transition.frame(.37f, panned)
        val back = GraphTransition.between(interrupted, home, source.camera)
        val first = back.frame(0f)
        assertEquals(panned, first.camera)
        for (node in interrupted.nodes) {
            val displayed = first.nodes.single { it.key == node.key }
            assertEquals(node.position, displayed.position)
            assertEquals(node.opacity, displayed.opacity, 0f)
            assertEquals(node.rootWeight, displayed.rootWeight, 0f)
            assertEquals(interrupted.camera.screen(node.position), first.camera.screen(displayed.position))
        }
        assertEquals(interrupted.edges.associate { (it.parent to it.child) to it.opacity },
            first.edges.associate { (it.parent to it.child) to it.opacity })
        assertEquals(GraphFrame.of(home, source.camera), back.frame(1f))
    }

    @Test fun hitTestingFollowsTheMovingNodeAndIgnoresBarelyVisibleOrRemovedItems() {
        val collapsed = ResourceGraphLayout.build(nodes, "folder", false)
        val expanded = ResourceGraphLayout.build(nodes, "folder", true)
        val transition = GraphTransition.between(GraphFrame.of(collapsed, GraphCamera()), expanded, GraphCamera())
        val appearing = transition.frame(.3f)
        assertNull(appearing.hit(appearing.nodes.single { it.key == "item" }.position))
        val legible = transition.frame(.9f)
        assertEquals("item", legible.hit(legible.nodes.single { it.key == "item" }.position)?.key)
        val deleting = GraphTransition.between(legible, collapsed, GraphCamera()).frame(0f)
        assertNull(deleting.hit(deleting.nodes.single { it.key == "item" }.position))
    }

    @Test(timeout = 10000) fun largeTransitionKeepsAllNodesAndEndpointsWithoutQuadraticMatching() {
        val all = (0 until 10000).map { ResourceNode("n$it", if (it == 0) null else "n0", NodeType.FOLDER, "目录 $it") }
        val home = ResourceGraphLayout.build(all, null, true)
        val child = ResourceGraphLayout.build(all, "n0", true)
        val transition = GraphTransition.between(GraphFrame.of(home, GraphCamera()), child, GraphCamera.fit(child.bounds, 360f, 600f))
        assertEquals(10001, transition.frame(.5f).nodes.size)
        assertEquals(10000, transition.frame(1f).nodes.size)
        assertEquals(child.nodes.map { it.resource?.id }, transition.frame(1f).nodes.map { it.resource?.id })
    }
}
