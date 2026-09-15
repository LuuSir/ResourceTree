package com.example.resouretree.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.resouretree.domain.model.ResourceNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlin.math.roundToInt

/** Only the active gesture uses a local order preview. The database remains the saved source of truth. */
@Composable
fun ReorderableNodeList(
    nodes: List<ResourceNode>, selectionMode: Boolean, selectedIds: Set<String>, busy: Boolean,
    canReorder: Boolean, onClick: (ResourceNode) -> Unit, onMenu: (ResourceNode) -> Unit,
    onReorder: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val latestNodes by rememberUpdatedState(nodes)
    val commit by rememberUpdatedState(onReorder)
    val latestBusy by rememberUpdatedState(busy)
    val scope = rememberCoroutineScope()
    val landingY = remember { Animatable(0f) }
    var preview by remember { mutableStateOf<List<ResourceNode>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var landingId by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    val enabled = canReorder && !selectionMode && !busy && landingId == null
    val floatingId = draggedId ?: landingId

    fun updateOrder() {
        val id = draggedId ?: return
        val current = preview ?: return
        val from = current.indexOfFirst { it.id == id }
        if (from < 0) return
        val info = listState.layoutInfo
        val center = (pointerY - grabOffset + draggedHeight / 2f)
            .coerceIn(info.viewportStartOffset.toFloat(), (info.viewportEndOffset - 1).toFloat())
        val target = info.visibleItemsInfo.firstOrNull { center >= it.offset && center < it.offset + it.size } ?: return
        val to = current.indexOfFirst { it.id == target.key }
        if (to >= 0 && to != from && current[to].isPinned == current[from].isPinned) {
            // Keep the viewport anchored to its position rather than to a key being dragged away.
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            preview = current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    fun cancelDrag() { draggedId = null; preview = null }

    fun finishDrag(save: Boolean) {
        val id = draggedId ?: return
        val order = preview?.map { it.id }
        val top = pointerY - grabOffset
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            landingY.snapTo(top)
            landingId = id
            draggedId = null
            if (!save) preview = latestNodes
            else if (order != null && order != latestNodes.map { it.id }) commit(order)
            try {
                withFrameNanos { }
                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset?.toFloat() ?: top
                landingY.animateTo(target, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMediumLow))
            } finally {
                landingId = null
                if (!latestBusy) preview = null
            }
        }
    }

    LaunchedEffect(nodes, busy) {
        if (draggedId == null && landingId == null && !busy) preview = null
        else if (draggedId != null && nodes.map { it.id }.toSet() != preview?.map { it.id }?.toSet()) cancelDrag()
    }
    LaunchedEffect(draggedId) {
        while (draggedId != null && isActive) {
            val info = listState.layoutInfo
            val speed = when {
                pointerY < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - pointerY) / edge).coerceIn(0f, 1f) * 20f
                pointerY > info.viewportEndOffset - edge -> ((pointerY - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f) * 20f
                else -> 0f
            }
            if (speed != 0f) { listState.scrollBy(speed); updateOrder() }
            delay(16)
        }
    }
    val shown = preview ?: nodes
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState, userScrollEnabled = floatingId == null, contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier.fillMaxSize().testTag("resource-list").pointerInput(enabled, selectionMode, busy) {
            // Consume long presses in search too, without turning them into an Item click on release.
            if (!selectionMode && !busy && landingId == null) detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
                    if (enabled && item != null) {
                        preview = latestNodes
                        draggedId = item.key as? String
                        pointerY = point.y; grabOffset = point.y - item.offset; draggedHeight = item.size
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onDrag = { change, amount ->
                    if (draggedId != null) { change.consume(); pointerY += amount.y; updateOrder() }
                },
                onDragCancel = { if (draggedId != null) finishDrag(save = false) },
                onDragEnd = { finishDrag(save = true) }
            )
        }
    ) {
        items(shown, key = { it.id }) { node ->
            val floating = node.id == floatingId
            Box(Modifier.testTag("node-${node.id}").animateItem(
                fadeInSpec = null, fadeOutSpec = null,
                placementSpec = if (floating) null else spring(stiffness = Spring.StiffnessLow, dampingRatio = .85f)
            )) {
                if (floating) Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { draggedHeight.toDp() }))
                else NodeRow(node, selectionMode = selectionMode, selected = node.id in selectedIds,
                    enabled = !busy && floatingId == null, onClick = { onClick(node) }, onMenu = { onMenu(node) })
            }
        }
    }
    // The dragged row is rendered outside the LazyColumn; a gap marks the destination in its layout.
    shown.firstOrNull { it.id == floatingId }?.let { node ->
        Box(Modifier.fillMaxWidth().testTag("drag-overlay").offset {
            IntOffset(0, (if (draggedId != null) pointerY - grabOffset else landingY.value).roundToInt())
        }.graphicsLayer { shadowElevation = 8.dp.toPx(); alpha = .98f }) {
            NodeRow(node, onClick = {}, onMenu = {}, enabled = false)
        }
    }
    }
}
