package com.example.resouretree.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.resouretree.domain.model.NodeType
import com.example.resouretree.domain.model.ResourceNode
import com.example.resouretree.ui.graph.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ResourceGraphView(all: List<ResourceNode>, currentId: String?, expanded: Boolean, enabled: Boolean, onNode: (ResourceNode) -> Unit) {
    val request = remember(all, currentId, expanded) { GraphRequest(all, GraphScope(currentId, expanded)) }
    val graph by produceState<LoadedGraph?>(null, request) {
        // Keep the last frame visible while computing the new layout. Cancelled requests never commit.
        value = withContext(Dispatchers.Default) {
            LoadedGraph(request, ResourceGraphLayout.build(request.all, request.scope.currentId, request.scope.expanded))
        }
    }
    val loaded = graph
    if (loaded == null) Box(Modifier.fillMaxSize().testTag("graph-loading"), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else GraphCanvas(loaded.graph, loaded.request.scope, enabled && loaded.request == request, onNode)
}

private data class GraphScope(val currentId: String?, val expanded: Boolean)
private data class GraphRequest(val all: List<ResourceNode>, val scope: GraphScope)
private data class LoadedGraph(val request: GraphRequest, val graph: ResourceGraph)
private data class GraphMotion(val transition: GraphTransition, val progress: Float = 1f, val cameraOverride: GraphCamera? = null) {
    fun frame() = transition.frame(progress, cameraOverride)
    fun camera() = transition.camera(progress, cameraOverride)
}

private val cameraSaver = listSaver<GraphCamera, Float>(
    save = { listOf(it.scale, it.x, it.y) }, restore = { GraphCamera(it[0], it[1], it[2]) })

@Composable
private fun GraphCanvas(graph: ResourceGraph, scope: GraphScope, enabled: Boolean, onNode: (ResourceNode) -> Unit) {
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var savedCamera by rememberSaveable(stateSaver = cameraSaver) { mutableStateOf(GraphCamera()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var displayed by remember { mutableStateOf(false) }
    var previousScope by remember { mutableStateOf(scope) }
    var previousViewport by remember { mutableStateOf(IntSize.Zero) }
    var motion by remember { mutableStateOf(GraphMotion(GraphTransition.still(GraphFrame.of(graph, savedCamera)))) }
    val frame by remember { derivedStateOf { motion.frame() } }
    val camera = frame.camera
    val width = viewport.width / density
    val height = viewport.height / density
    val fit = remember(graph.bounds, viewport, density) { GraphCamera.fit(graph.bounds, width, height) }
    val minimum = min(.25f, fit.scale)
    val zoomLabel = if (camera.scale < .01f) "<1%" else "${(camera.scale * 100).roundToInt()}%"
    LaunchedEffect(graph, scope, viewport, density) {
        if (width > 0f && height > 0f) {
            val source = motion.frame()
            val targetCamera = when {
                !initialized -> fit
                !displayed -> savedCamera
                previousScope != scope || previousViewport != viewport -> fit
                else -> source.camera
            }
            previousScope = scope; previousViewport = viewport
            initialized = true
            if (!displayed) {
                motion = GraphMotion(GraphTransition.still(GraphFrame.of(graph, targetCamera)))
                displayed = true; savedCamera = targetCamera
            } else {
                val transition = GraphTransition.between(source, graph, targetCamera)
                // Position, opacity and camera are reset atomically, including interrupted transitions.
                motion = GraphMotion(transition, 0f)
                animate(0f, 1f, animationSpec = tween(420, easing = FastOutSlowInEasing)) { progress, _ ->
                    motion = motion.copy(progress = progress)
                    savedCamera = motion.camera()
                }
            }
        }
    }
    fun moveCamera(next: GraphCamera) {
        // A gesture takes control of the camera without freezing partly appearing/disappearing nodes.
        motion = motion.copy(cameraOverride = next)
        savedCamera = next
    }
    val click by rememberUpdatedState(onNode)
    val allowed by rememberUpdatedState(enabled)
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    val visible = remember(frame, viewport, density) {
        frame.nodes.filter { node ->
            val point = camera.screen(node.position)
            abs(point.x) <= width / 2 + ResourceGraphLayout.NODE_WIDTH * camera.scale / 2 &&
                abs(point.y) <= height / 2 + ResourceGraphLayout.NODE_HEIGHT * camera.scale / 2
        }
    }
    val byId = remember(frame.nodes) { frame.nodes.associateBy { it.key } }
    Column(Modifier.fillMaxSize().testTag("graph-view")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${graph.nodes.size - 1} 个节点", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text("双指缩放 · 拖动画布", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(colors.surfaceContainerLowest)
            .onSizeChanged { viewport = it }.testTag("graph-canvas")
            .semantics { contentDescription = "资源图，${graph.nodes.size - 1} 个节点"; stateDescription = "缩放 $zoomLabel" }
            .pointerInput(graph, viewport, density, minimum) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var multiple = false
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    var cancelled = false
                    do {
                        val event = awaitPointerEvent()
                        multiple = multiple || event.changes.count { it.pressed || it.previousPressed } > 1
                        if (event.changes.any { it.isConsumed }) cancelled = true
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPan += pan; totalZoom *= zoom
                        if (totalPan.getDistance() > viewConfiguration.touchSlop ||
                            abs(1 - totalZoom) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop) moved = true
                        if (moved && !cancelled) {
                            val pivot = event.calculateCentroid(useCurrent = false)
                            if (pivot != Offset.Unspecified) moveCamera(motion.camera().transform(
                                GraphPoint(pivot.x / density - width / 2, pivot.y / density - height / 2),
                                GraphPoint(pan.x / density, pan.y / density), zoom, minimum))
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!moved && !multiple && !cancelled && allowed) {
                        val current = motion.frame()
                        val world = current.camera.world(GraphPoint(down.position.x / density - width / 2, down.position.y / density - height / 2))
                        current.hit(world)?.resource?.let(click)
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val cameraX = size.width / 2 + camera.x * density
                val cameraY = size.height / 2 + camera.y * density
                withTransform({ translate(cameraX, cameraY); scale(camera.scale, camera.scale, Offset.Zero) }) {
                    fun offset(point: GraphPoint) = Offset(point.x * density, point.y * density)
                    for (edge in frame.edges) {
                        val node = byId[edge.child] ?: continue
                        val parent = byId[edge.parent] ?: continue
                        val a = camera.screen(node.position); val b = camera.screen(parent.position)
                        if (min(a.x, b.x) > width / 2 || maxOf(a.x, b.x) < -width / 2 ||
                            min(a.y, b.y) > height / 2 || maxOf(a.y, b.y) < -height / 2) continue
                        drawLine(colors.outlineVariant.copy(alpha = edge.opacity), offset(parent.position), offset(node.position), 1.5f * density / camera.scale.coerceAtLeast(.25f))
                    }
                    for (node in visible) {
                        val folder = node.resource == null || node.resource.type == NodeType.FOLDER
                        val origin = offset(node.position) - Offset(64f * density, 32f * density)
                        val fill = lerp(if (folder) colors.tertiaryContainer else colors.secondaryContainer, Color(0xFFE78043), node.rootWeight).copy(alpha = node.opacity)
                        val ink = lerp(if (folder) colors.onTertiaryContainer else colors.onSecondaryContainer, Color(0xFF28160C), node.rootWeight).copy(alpha = node.opacity)
                        val card = Size(128f * density, 64f * density)
                        drawRoundRect(fill, origin, card, CornerRadius(14f * density))
                        drawRoundRect(lerp(colors.outlineVariant, Color(0xFFBB5926), node.rootWeight).copy(alpha = node.opacity), origin, card, CornerRadius(14f * density), style = Stroke(density))
                        if (camera.scale >= .4f) {
                            val icon = offset(node.position) + Offset(-7f * density, -22f * density)
                            if (folder) {
                                drawRoundRect(ink, icon, Size(8f * density, 5f * density), CornerRadius(2f * density))
                                drawRoundRect(ink, icon + Offset(0f, 3f * density), Size(15f * density, 10f * density), CornerRadius(2f * density))
                            } else drawCircle(ink, 5f * density, icon + Offset(7f * density, 7f * density))
                            if (node.resource?.isPinned == true) drawCircle(ink, 2.5f * density, origin + Offset(115f * density, 12f * density))
                            drawText(textMeasurer, node.name, topLeft = origin + Offset(8f * density, 32f * density),
                                style = titleStyle.copy(color = ink), overflow = TextOverflow.Ellipsis, maxLines = 1,
                                size = Size(112f * density, 26f * density))
                        }
                    }
                }
            }
            // Only legible, visible nodes need individual accessibility elements. Canvas retains all nodes.
            for (node in visible) if (node.present && (node.depth == 0 || (node.opacity >= .75f && camera.scale >= .4f))) {
                key(node.key) {
                    val point = camera.screen(node.position)
                    Box(Modifier.absoluteOffset { IntOffset(
                        ((width / 2 + point.x - 64 * camera.scale) * density).roundToInt(),
                        ((height / 2 + point.y - 32 * camera.scale) * density).roundToInt()) }
                        .size((128 * camera.scale).dp, (64 * camera.scale).dp)
                        .testTag(if (node.depth == 0) "graph-root" else "graph-node-${node.resource!!.id}")
                        .semantics {
                            contentDescription = "${if (node.depth == 0) "当前目录" else if (node.resource?.type == NodeType.FOLDER) "文件夹" else "条目"}：${node.name}"
                            if (node.interactive) {
                                role = Role.Button
                                if (!enabled) disabled()
                                onClick(if (node.resource?.type == NodeType.FOLDER) "进入文件夹" else "执行条目动作") {
                                    val current = motion.frame().nodes.find { it.key == node.key }
                                    if (allowed && current?.interactive == true && current.opacity >= .75f) current.resource?.let(click)
                                    allowed && current?.interactive == true
                                }
                            }
                        })
                }
            }
            if (graph.nodes.size == 1) Text("这里还没有内容，可切回列表新建", Modifier.align(Alignment.BottomCenter).padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { moveCamera(camera.transform(GraphPoint(0f, 0f), GraphPoint(0f, 0f), 1 / 1.4f, minimum)) }, modifier = Modifier.semantics { contentDescription = "缩小" }) { Text("−") }
            TextButton(onClick = { moveCamera(fit) }) { Text("适应画布") }
            Text(zoomLabel, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { moveCamera(camera.transform(GraphPoint(0f, 0f), GraphPoint(0f, 0f), 1.4f, minimum)) }, modifier = Modifier.semantics { contentDescription = "放大" }) { Text("＋") }
        }
    }
}
