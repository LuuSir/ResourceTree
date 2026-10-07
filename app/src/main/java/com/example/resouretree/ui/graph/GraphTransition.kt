package com.example.resouretree.ui.graph

import com.example.resouretree.domain.model.ResourceNode

private const val HOME_KEY = "\u0000home"
private val GraphNode.key get() = resource?.id ?: HOME_KEY

/** A displayed frame is also the starting point of an interrupted navigation. */
data class GraphFrameNode(
    val key: String,
    val resource: ResourceNode?,
    val depth: Int,
    val position: GraphPoint,
    val opacity: Float = 1f,
    val rootWeight: Float = if (depth == 0) 1f else 0f,
    val interactive: Boolean = depth > 0,
    val present: Boolean = true,
) {
    val name get() = resource?.name ?: "首页"
}

data class GraphFrameEdge(val parent: String, val child: String, val opacity: Float = 1f)

data class GraphFrame(val nodes: List<GraphFrameNode>, val edges: List<GraphFrameEdge>, val camera: GraphCamera) {
    fun hit(point: GraphPoint): GraphFrameNode? = nodes.asReversed().firstOrNull {
        it.present && it.interactive && it.opacity >= .75f &&
            kotlin.math.abs(point.x - it.position.x) <= ResourceGraphLayout.NODE_WIDTH / 2 &&
            kotlin.math.abs(point.y - it.position.y) <= ResourceGraphLayout.NODE_HEIGHT / 2
    }

    companion object {
        fun of(graph: ResourceGraph, camera: GraphCamera) = GraphFrame(
            graph.nodes.map { GraphFrameNode(it.key, it.resource, it.depth, it.position) },
            graph.nodes.mapNotNull { node -> node.parent?.let { GraphFrameEdge(graph.nodes[it].key, node.key) } },
            camera,
        )
    }
}

/** One timeline moves nodes, fades edges, and fits the camera together. Maps keep planning O(n). */
class GraphTransition private constructor(
    private val nodes: List<Pair<GraphFrameNode, GraphFrameNode>>,
    private val edges: List<Pair<GraphFrameEdge, GraphFrameEdge>>,
    private val startCamera: GraphCamera,
    private val endCamera: GraphCamera,
) {
    fun camera(progress: Float, override: GraphCamera? = null): GraphCamera {
        val t = progress.coerceIn(0f, 1f)
        return override ?: GraphCamera(mix(startCamera.scale, endCamera.scale, t),
            mix(startCamera.x, endCamera.x, t), mix(startCamera.y, endCamera.y, t))
    }

    fun frame(progress: Float, cameraOverride: GraphCamera? = null): GraphFrame {
        val t = progress.coerceIn(0f, 1f)
        return GraphFrame(nodes.mapNotNull { (start, end) ->
            val opacity = mix(start.opacity, end.opacity, t)
            if (opacity <= 0f) null else end.copy(
                position = GraphPoint(mix(start.position.x, end.position.x, t), mix(start.position.y, end.position.y, t)),
                opacity = opacity,
                rootWeight = mix(start.rootWeight, end.rootWeight, t),
            )
        }, edges.mapNotNull { (start, end) ->
            val opacity = mix(start.opacity, end.opacity, t)
            if (opacity <= 0f) null else end.copy(opacity = opacity)
        }, camera(t, cameraOverride))
    }

    companion object {
        fun still(frame: GraphFrame) = GraphTransition(frame.nodes.map { it to it }, frame.edges.map { it to it }, frame.camera, frame.camera)

        fun between(source: GraphFrame, graph: ResourceGraph, camera: GraphCamera): GraphTransition {
            val target = GraphFrame.of(graph, camera)
            val before = source.nodes.associateBy { it.key }
            val after = target.nodes.associateBy { it.key }
            val parents = target.edges.associate { it.child to it.parent }
            // Traversal is breadth first, so an appearing node inherits its parent's resolved origin.
            val origins = HashMap<String, GraphPoint>(after.size)
            for (node in target.nodes) origins[node.key] = before[node.key]?.position
                ?: parents[node.key]?.let { origins[it] }
                ?: source.nodes.firstOrNull { it.depth == 0 }?.position ?: GraphPoint(0f, 0f)
            val centre = target.nodes.first().position
            val movements = target.nodes.map { end ->
                (before[end.key] ?: end.copy(position = origins.getValue(end.key), opacity = 0f)) to end
            } + source.nodes.filter { it.key !in after }.map { start ->
                start to start.copy(position = centre, opacity = 0f, interactive = false, present = false)
            }
            val oldEdges = source.edges.associateBy { it.parent to it.child }
            val newEdges = target.edges.associateBy { it.parent to it.child }
            val lines = target.edges.map { end -> (oldEdges[end.parent to end.child] ?: end.copy(opacity = 0f)) to end } +
                source.edges.filter { (it.parent to it.child) !in newEdges }.map { it to it.copy(opacity = 0f) }
            return GraphTransition(movements, lines, source.camera, camera)
        }
    }
}

private fun mix(start: Float, end: Float, progress: Float) = when (progress) {
    0f -> start
    1f -> end
    else -> start + (end - start) * progress
}
