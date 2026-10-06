package com.example.resouretree.ui.components

import androidx.compose.foundation.Canvas
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
    val graph by produceState<ResourceGraph?>(null, all, currentId, expanded) {
        value = null
        value = withContext(Dispatchers.Default) { ResourceGraphLayout.build(all, currentId, expanded) }
    }
    val loaded = graph
    if (loaded == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else key(currentId, expanded) { GraphCanvas(loaded, enabled, onNode) }
}

private val cameraSaver = listSaver<GraphCamera, Float>(
    save = { listOf(it.scale, it.x, it.y) }, restore = { GraphCamera(it[0], it[1], it[2]) })

@Composable
private fun GraphCanvas(graph: ResourceGraph, enabled: Boolean, onNode: (ResourceNode) -> Unit) {
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var camera by rememberSaveable(stateSaver = cameraSaver) { mutableStateOf(GraphCamera()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    val width = viewport.width / density
    val height = viewport.height / density
    val fit = remember(graph.bounds, viewport, density) { GraphCamera.fit(graph.bounds, width, height) }
    val minimum = min(.25f, fit.scale)
    val zoomLabel = if (camera.scale < .01f) "<1%" else "${(camera.scale * 100).roundToInt()}%"
    LaunchedEffect(viewport, graph.bounds) {
        // Use the dimensions captured with this fit value, not a newer onSizeChanged state.
        if (width > 0f && height > 0f) {
            if (!initialized) { camera = fit; initialized = true }
            else if (camera.scale < minimum) camera = camera.copy(scale = minimum)
        }
    }
    val click by rememberUpdatedState(onNode)
    val allowed by rememberUpdatedState(enabled)
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    val visible = remember(graph, camera, viewport, density) {
        graph.nodes.filter { node ->
            val point = camera.screen(node.position)
            abs(point.x) <= width / 2 + ResourceGraphLayout.NODE_WIDTH * camera.scale / 2 &&
                abs(point.y) <= height / 2 + ResourceGraphLayout.NODE_HEIGHT * camera.scale / 2
        }
    }
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
                            if (pivot != Offset.Unspecified) camera = camera.transform(
                                GraphPoint(pivot.x / density - width / 2, pivot.y / density - height / 2),
                                GraphPoint(pan.x / density, pan.y / density), zoom, minimum)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!moved && !multiple && !cancelled && allowed) {
                        val world = camera.world(GraphPoint(down.position.x / density - width / 2, down.position.y / density - height / 2))
                        graph.hit(world)?.takeIf { it.depth > 0 }?.resource?.let(click)
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val cameraX = size.width / 2 + camera.x * density
                val cameraY = size.height / 2 + camera.y * density
                withTransform({ translate(cameraX, cameraY); scale(camera.scale, camera.scale, Offset.Zero) }) {
                    fun offset(point: GraphPoint) = Offset(point.x * density, point.y * density)
                    for (node in graph.nodes) {
                        val parent = node.parent?.let { graph.nodes[it] } ?: continue
                        val a = camera.screen(node.position); val b = camera.screen(parent.position)
                        if (min(a.x, b.x) > width / 2 || maxOf(a.x, b.x) < -width / 2 ||
                            min(a.y, b.y) > height / 2 || maxOf(a.y, b.y) < -height / 2) continue
                        drawLine(colors.outlineVariant, offset(parent.position), offset(node.position), 1.5f * density / camera.scale.coerceAtLeast(.25f))
                    }
                    for (node in visible) {
                        val root = node.depth == 0
                        val folder = root || node.resource?.type == NodeType.FOLDER
                        val origin = offset(node.position) - Offset(64f * density, 32f * density)
                        val fill = if (root) Color(0xFFE78043) else if (folder) colors.tertiaryContainer else colors.secondaryContainer
                        val ink = if (root) Color(0xFF28160C) else if (folder) colors.onTertiaryContainer else colors.onSecondaryContainer
                        val card = Size(128f * density, 64f * density)
                        drawRoundRect(fill, origin, card, CornerRadius(14f * density))
                        drawRoundRect(if (root) Color(0xFFBB5926) else colors.outlineVariant, origin, card, CornerRadius(14f * density), style = Stroke(density))
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
            for (node in visible) if (camera.scale >= .4f || node.depth == 0) {
                key(node.resource?.id, node.depth == 0) {
                    val point = camera.screen(node.position)
                    Box(Modifier.absoluteOffset { IntOffset(
                        ((width / 2 + point.x - 64 * camera.scale) * density).roundToInt(),
                        ((height / 2 + point.y - 32 * camera.scale) * density).roundToInt()) }
                        .size((128 * camera.scale).dp, (64 * camera.scale).dp)
                        .testTag(if (node.depth == 0) "graph-root" else "graph-node-${node.resource!!.id}")
                        .semantics {
                            contentDescription = "${if (node.depth == 0) "当前目录" else if (node.resource?.type == NodeType.FOLDER) "文件夹" else "条目"}：${node.name}"
                            if (node.depth > 0) {
                                role = Role.Button
                                if (!enabled) disabled()
                                onClick(if (node.resource?.type == NodeType.FOLDER) "进入文件夹" else "执行条目动作") {
                                    if (enabled) node.resource?.let(click)
                                    enabled
                                }
                            }
                        })
                }
            }
            if (graph.nodes.size == 1) Text("这里还没有内容，可切回列表新建", Modifier.align(Alignment.BottomCenter).padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { camera = camera.transform(GraphPoint(0f, 0f), GraphPoint(0f, 0f), 1 / 1.4f, minimum) }, modifier = Modifier.semantics { contentDescription = "缩小" }) { Text("−") }
            TextButton(onClick = { camera = fit }) { Text("适应画布") }
            Text(zoomLabel, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { camera = camera.transform(GraphPoint(0f, 0f), GraphPoint(0f, 0f), 1.4f, minimum) }, modifier = Modifier.semantics { contentDescription = "放大" }) { Text("＋") }
        }
    }
}
