package com.example.resouretree.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.components.ReorderableNodeList
import com.example.resouretree.ui.viewmodel.BrowserViewModel

private data class TransferRequest(val ids: Set<String>, val copy: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: BrowserViewModel, onCreate: (NodeType, String?) -> Unit, onEdit: (ResourceNode) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ResourceNode?>(null) }
    var deleting by remember { mutableStateOf<Set<String>?>(null) }
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    var selectionMode by rememberSaveable(state.currentId, searching, state.query) { mutableStateOf(false) }
    var selectedIds by rememberSaveable(state.currentId, searching, state.query) { mutableStateOf(arrayListOf<String>()) }
    val rows = if (searching) { if (state.query.isBlank()) emptyList() else state.results } else state.children
    val checked = selectedIds.toSet().intersect(rows.map { it.id }.toSet())
    val clearSelection = { selectedIds = arrayListOf<String>(); selectionMode = false }
    val toggle: (String) -> Unit = { id ->
        if (!state.busy) selectedIds = ArrayList(if (id in checked) checked - id else checked + id)
    }
    var about by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importDocument) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportDocument) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(selectionMode || searching || state.currentId != null) {
        if (!state.busy) {
            if (selectionMode) clearSelection()
            else if (searching) { searching = false; vm.search("") } else vm.up()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (selectionMode) "已选 ${checked.size} 项" else if (searching) "全局搜索" else "ResourceTree") },
                navigationIcon = { if (selectionMode || searching || state.currentId != null) TextButton(enabled = !state.busy, onClick = {
                    if (selectionMode) clearSelection()
                    else if (searching) { searching = false; vm.search("") } else vm.up()
                }) { Text(if (selectionMode) "取消" else "返回") } },
                actions = {
                    if (selectionMode) {
                        val allSelected = rows.isNotEmpty() && checked.size == rows.size
                        TextButton(enabled = !state.busy && rows.isNotEmpty(), onClick = {
                            selectedIds = if (allSelected) arrayListOf() else ArrayList(rows.map { it.id })
                        }) { Text(if (allSelected) "取消全选" else "全选") }
                    } else {
                    if (!searching) TextButton(enabled = !state.busy, onClick = { searching = true }) { Text("搜索") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("菜单") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("导入 JSON") }, enabled = !state.busy && !state.loading, onClick = {
                                menu = false
                                try { importer.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                                catch (_: Exception) { vm.notify("未找到系统文件选择器") }
                            })
                            DropdownMenuItem(text = { Text("导出 JSON") }, enabled = !state.busy && !state.loading, onClick = {
                                menu = false
                                try { exporter.launch("ResourceTree-${java.time.LocalDate.now()}.json") }
                                catch (_: Exception) { vm.notify("未找到系统文件选择器") }
                            })
                            DropdownMenuItem(text = { Text("关于") }, onClick = { menu = false; about = true })
                        }
                    }
                    }
                })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selectionMode) BottomAppBar {
                TextButton(modifier = Modifier.weight(1f), enabled = checked.isNotEmpty() && !state.busy,
                    onClick = { transfer = TransferRequest(checked, false) }) { Text("移动") }
                TextButton(modifier = Modifier.weight(1f), enabled = checked.isNotEmpty() && !state.busy,
                    onClick = { transfer = TransferRequest(checked, true) }) { Text("复制") }
                TextButton(modifier = Modifier.weight(1f), enabled = checked.isNotEmpty() && !state.busy,
                    onClick = { deleting = checked }) { Text("删除") }
            }
        },
        floatingActionButton = { if (!selectionMode && !searching && !state.loading && state.error == null && !state.busy) ExtendedFloatingActionButton(onClick = { create = true }) { Text("＋ 新建") } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (searching) OutlinedTextField(state.query, vm::search, enabled = !selectionMode && !state.busy, label = { Text("搜索名称、内容、标签") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !selectionMode && !state.busy, onClick = { vm.open(null) }) { Text("首页") }
                state.breadcrumb.forEach { node -> Text("›"); TextButton(enabled = !selectionMode && !state.busy, onClick = { vm.open(node.id) }) { Text(node.name) } }
            }
            when {
                state.error != null -> CenterMessage(state.error.orEmpty(), "重试", vm::initialize)
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                searching && state.query.isBlank() -> CenterMessage("输入关键词，搜索所有目录中的条目")
                rows.isEmpty() -> CenterMessage(if (searching) "没有匹配的条目" else "这里还没有内容\n点击 + 创建文件夹或条目")
                else -> key(state.currentId, searching) {
                    ReorderableNodeList(rows, selectionMode, checked, state.busy, canReorder = !searching,
                        onClick = { node -> if (selectionMode) toggle(node.id) else if (node.type == NodeType.FOLDER) vm.open(node.id) else vm.execute(node) },
                        onMenu = { node -> selected = node },
                        onReorder = { vm.reorder(state.currentId, it) })
                }
            }
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("在当前目录新建") }, text = {
        Column { NodeType.entries.forEach { type -> TextButton(onClick = { create = false; onCreate(type, state.currentId) }) { Text(if (type == NodeType.FOLDER) "新建文件夹" else "新建条目") } } }
    }, confirmButton = { TextButton(onClick = { create = false }) { Text("取消") } })
    selected?.let { node -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(node.name) }, text = {
        Column {
            TextButton(enabled = !state.busy, onClick = { selected = null; vm.setPinned(node) }) { Text(if (node.isPinned) "取消置顶" else "置顶") }
            TextButton(enabled = !state.busy, onClick = { selected = null; onEdit(node) }) { Text("编辑") }
            TextButton(enabled = !state.busy, onClick = { selected = null; selectionMode = true; selectedIds = arrayListOf(node.id) }) { Text("多选") }
            TextButton(enabled = !state.busy, onClick = { selected = null; transfer = TransferRequest(setOf(node.id), false) }) { Text("移动") }
            TextButton(enabled = !state.busy, onClick = { selected = null; transfer = TransferRequest(setOf(node.id), true) }) { Text("复制") }
            TextButton(enabled = !state.busy, onClick = { selected = null; deleting = setOf(node.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { selected = null }) { Text("取消") } }) }
    deleting?.let { ids ->
        val total = ids.flatMap { Tree.descendants(state.all, it) }.toSet().size
        val single = state.all.find { it.id == ids.singleOrNull() }
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (single != null) "删除“${single.name}”？" else "删除所选 ${ids.size} 项？") },
        text = { Text("将删除 $total 个节点，包括所选目录中的全部子目录和条目。此操作无法撤销。") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { deleting = null; vm.deleteMany(ids, clearSelection) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    transfer?.let { request ->
        val excluded = remember(state.all, request) {
            if (request.copy) emptySet() else request.ids.flatMap { Tree.descendants(state.all, it) }.toSet()
        }
        val folders = state.all.filter { it.type == NodeType.FOLDER && it.id !in excluded }
        val destination: (String?) -> Unit = { parent ->
            transfer = null; vm.transferMany(request.ids, parent, request.copy, clearSelection)
        }
        AlertDialog(onDismissRequest = { transfer = null }, title = { Text("${if (request.copy) "复制" else "移动"} ${request.ids.size} 项到…") }, text = {
            LazyColumn(Modifier.heightIn(max = 350.dp)) {
                if (request.copy) item { Text("文件夹将连同全部子节点复制；同名时自动添加“副本”。") }
                item { TextButton(enabled = !state.busy, onClick = { destination(null) }) { Text("首页") } }
                items(folders, key = { it.id }) { folder ->
                    TextButton(enabled = !state.busy, onClick = { destination(folder.id) }) {
                        Text("首页 / " + Tree.breadcrumb(state.all, folder.id).joinToString(" / ") { it.name })
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { transfer = null }) { Text("取消") } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("ResourceTree 0.1") },
        text = { Text("本地树状快捷资源管理器\n\n所有资源保存在设备上。导入会追加到首页，不覆盖已有内容。\n\n复制后将打开指定应用；目标应用是否识别剪贴板由该应用决定。") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("知道了") } })
}

@Composable
private fun CenterMessage(text: String, action: String? = null, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (action != null) Button(onClick = onClick) { Text(action) }
        }
    }
}
