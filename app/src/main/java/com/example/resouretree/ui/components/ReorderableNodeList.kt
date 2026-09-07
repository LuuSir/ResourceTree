package com.example.resouretree.ui.components

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
import androidx.compose.ui.zIndex
import com.example.resouretree.domain.model.ResourceNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    var preview by remember { mutableStateOf<List<ResourceNode>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    val enabled = canReorder && !selectionMode && !busy

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

    LaunchedEffect(nodes, busy) {
        if (draggedId == null && !busy) preview = null
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
    LazyColumn(
        state = listState, userScrollEnabled = draggedId == null, contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier.fillMaxSize().testTag("resource-list").pointerInput(enabled, selectionMode, busy) {
            // Consume long presses in search too, without turning them into an Item click on release.
            if (!selectionMode && !busy) detectDragGesturesAfterLongPress(
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
                onDragCancel = { if (draggedId != null) cancelDrag() },
                onDragEnd = {
                    val order = preview?.map { it.id }
                    draggedId = null
                    if (order != null && order != latestNodes.map { it.id }) commit(order) else preview = null
                }
            )
        }
    ) {
        items(shown, key = { it.id }) { node ->
            val dragging = node.id == draggedId
            val itemOffset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == node.id }?.offset ?: 0
            Box(Modifier.testTag("node-${node.id}").zIndex(if (dragging) 1f else 0f).graphicsLayer {
                translationY = if (dragging) pointerY - grabOffset - itemOffset else 0f
                shadowElevation = if (dragging) 8.dp.toPx() else 0f
                alpha = if (dragging) .95f else 1f
            }) {
                NodeRow(node, selectionMode = selectionMode, selected = node.id in selectedIds, enabled = !busy,
                    onClick = { onClick(node) }, onMenu = { onMenu(node) })
            }
        }
    }
}
