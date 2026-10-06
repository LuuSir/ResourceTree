package com.example.resouretree.ui.graph

import com.example.resouretree.domain.model.NodeType
import com.example.resouretree.domain.model.ResourceNode
import com.example.resouretree.domain.model.Tree
import kotlin.math.*

data class GraphPoint(val x: Float, val y: Float)
data class GraphBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
data class GraphNode(val resource: ResourceNode?, val parent: Int?, val depth: Int, val position: GraphPoint) {
    val name get() = resource?.name ?: "首页"
}
data class ResourceGraph(val nodes: List<GraphNode>, val bounds: GraphBounds)

/** Deterministic radial tree in dp. Iterative traversal also handles the maximum imported depth. */
object ResourceGraphLayout {
    const val NODE_WIDTH = 128f
    const val NODE_HEIGHT = 64f
    private const val SPACING = 176.0 // Larger than the node's bounding-circle diameter.

    fun build(all: List<ResourceNode>, currentId: String?, expanded: Boolean): ResourceGraph {
        val root = all.find { it.id == currentId && it.type == NodeType.FOLDER }
        val children = all.groupBy { it.parentId }.mapValues { (_, value) -> value.sortedWith(Tree.order) }
        val resources = mutableListOf(root)
        val parents = mutableListOf<Int?>(null)
        val depths = mutableListOf(0)
        val childIndices = mutableListOf(mutableListOf<Int>())
        val visited = mutableSetOf<String>().apply { root?.id?.let(::add) }
        var index = 0
        while (index < resources.size) {
            if (index == 0 || (expanded && resources[index]?.type == NodeType.FOLDER)) {
                for (child in children[resources[index]?.id].orEmpty()) {
                    if (!visited.add(child.id)) continue
                    childIndices[index].add(resources.size)
                    resources.add(child); parents.add(index); depths.add(depths[index] + 1)
                    childIndices.add(mutableListOf())
                }
            }
            index++
        }
        val weights = IntArray(resources.size) { 1 }
        for (i in resources.indices.reversed()) {
            if (childIndices[i].isNotEmpty()) weights[i] = childIndices[i].sumOf { weights[it] }
        }
        val starts = DoubleArray(resources.size) { -PI / 2 }
        val spans = DoubleArray(resources.size) { 2 * PI }
        val angles = DoubleArray(resources.size)
        for (i in resources.indices) {
            angles[i] = starts[i] + spans[i] / 2
            var cursor = starts[i]
            for (child in childIndices[i]) {
                starts[child] = cursor
                spans[child] = spans[i] * weights[child] / weights[i]
                cursor += spans[child]
            }
        }
        val rings = DoubleArray((depths.maxOrNull() ?: 0) + 1)
        val levels = resources.indices.groupBy { depths[it] }
        for (level in 1 until rings.size) {
            val ordered = levels.getValue(level) // Breadth-first insertion preserves angular order.
            var radius = rings[level - 1] + SPACING
            if (ordered.size > 1) {
                var gap = 2 * PI - angles[ordered.last()] + angles[ordered.first()]
                for (j in 1 until ordered.size) gap = min(gap, angles[ordered[j]] - angles[ordered[j - 1]])
                radius = max(radius, SPACING / (2 * sin(gap / 2)))
            }
            rings[level] = radius
        }
        val nodes = resources.indices.map { i ->
            val radius = rings[depths[i]]
            GraphNode(resources[i], parents[i], depths[i], GraphPoint((cos(angles[i]) * radius).toFloat(), (sin(angles[i]) * radius).toFloat()))
        }
        return ResourceGraph(nodes, GraphBounds(
            nodes.minOf { it.position.x } - NODE_WIDTH / 2 - 24,
            nodes.minOf { it.position.y } - NODE_HEIGHT / 2 - 24,
            nodes.maxOf { it.position.x } + NODE_WIDTH / 2 + 24,
            nodes.maxOf { it.position.y } + NODE_HEIGHT / 2 + 24
        ))
    }
}

/** Camera uses viewport-centred dp, independent of display density. */
data class GraphCamera(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun world(point: GraphPoint) = GraphPoint((point.x - x) / scale, (point.y - y) / scale)
    fun screen(point: GraphPoint) = GraphPoint(point.x * scale + x, point.y * scale + y)
    fun transform(pivot: GraphPoint, pan: GraphPoint, zoom: Float, minScale: Float): GraphCamera {
        val next = (scale * zoom).coerceIn(minScale, 3f)
        val ratio = next / scale
        return GraphCamera(next, pivot.x - (pivot.x - x) * ratio + pan.x, pivot.y - (pivot.y - y) * ratio + pan.y)
    }
    companion object {
        fun fit(bounds: GraphBounds, width: Float, height: Float): GraphCamera {
            val scale = min(1f, min(width.coerceAtLeast(1f) / bounds.width, height.coerceAtLeast(1f) / bounds.height))
            return GraphCamera(scale, -(bounds.left + bounds.right) / 2 * scale, -(bounds.top + bounds.bottom) / 2 * scale)
        }
    }
}

fun ResourceGraph.hit(point: GraphPoint): GraphNode? = nodes.firstOrNull {
    abs(point.x - it.position.x) <= ResourceGraphLayout.NODE_WIDTH / 2 &&
        abs(point.y - it.position.y) <= ResourceGraphLayout.NODE_HEIGHT / 2
}
