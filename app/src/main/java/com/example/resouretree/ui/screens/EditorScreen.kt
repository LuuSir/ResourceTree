package com.example.resouretree.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.resouretree.domain.model.*
import com.example.resouretree.ui.viewmodel.EditorViewModel
import com.example.resouretree.ui.components.AppPicker
import com.example.resouretree.ui.components.ApplicationIcon

fun ActionType.label(): String = when (this) {
    ActionType.NONE -> "无动作"; ActionType.COPY -> "复制文字"
    ActionType.SHARE -> "分享"
    ActionType.LAUNCH_APP -> "打开 App"; ActionType.COPY_AND_LAUNCH -> "复制并打开 App"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, editing: Boolean, fromClipboard: Boolean = false, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dirty by rememberSaveable { mutableStateOf(fromClipboard) }
    var discard by remember { mutableStateOf(false) }
    var chooseApp by rememberSaveable { mutableStateOf(false) }
    val working = state.saving || state.importingMedia
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { dirty = true; vm.selectMedia(uri) }
    }
    val needsApp = state.type == NodeType.ITEM && state.actionType in listOf(ActionType.LAUNCH_APP, ActionType.COPY_AND_LAUNCH, ActionType.SHARE)
    LaunchedEffect(needsApp, state.loading) { if (needsApp && !state.loading) vm.loadApps() }
    val back = { if (dirty && !state.saved) discard = true else onBack() }
    BackHandler(enabled = !state.saved) { if (!working) back() }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("${if (editing) "编辑" else "新建"}${if (state.type == NodeType.FOLDER) "文件夹" else "条目"}") },
            navigationIcon = { TextButton(onClick = back, enabled = !working) { Text("返回") } },
            actions = { TextButton(onClick = vm::save, enabled = !state.loading && !working && !state.loadFailed) { Text(if (state.saving) "保存中" else "保存") } })
    }) { padding ->
        if (state.loading) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(state.name, { dirty = true; vm.change { s -> s.copy(name = it) } }, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth(), enabled = !working && !state.loadFailed)
            if (state.type == NodeType.ITEM && !state.loadFailed) {
                Text("内容类型", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentType.entries.forEach { type ->
                        FilterChip(selected = state.content.type == type, enabled = !working,
                            onClick = { dirty = true; vm.selectContentType(type) }, label = { Text(type.name) })
                    }
                }
                if (state.content.type == ContentType.TEXT) {
                    OutlinedTextField(state.content.text, { value -> dirty = true; vm.change { it.copy(content = it.content.copy(text = value)) } },
                        label = { Text("文本内容") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !working)
                } else {
                    OutlinedButton(enabled = !working, onClick = {
                        val mime = when (state.content.type) { ContentType.IMAGE -> "image/*"; ContentType.VIDEO -> "video/*"; else -> "*/*" }
                        try { mediaPicker.launch(arrayOf(mime)) } catch (_: Exception) { vm.mediaError() }
                    }) { Text(if (state.importingMedia) "正在保存文件…" else if (state.content.path.isBlank()) "选择媒体文件" else "重新选择文件") }
                    if (state.content.path.isNotBlank()) Text(state.content.path, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.content.mimeType, { value -> dirty = true; vm.change { it.copy(content = it.content.copy(mimeType = value.trim())) } },
                        label = { Text("MIME 类型") }, supportingText = { Text("选择文件后自动填写") }, enabled = !working, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(state.tags, { dirty = true; vm.change { s -> s.copy(tags = it) } }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth(), enabled = !working)
                Text("点击条目时", style = MaterialTheme.typography.titleSmall)
                ActionType.entries.filter { state.content.type == ContentType.TEXT || it !in listOf(ActionType.COPY, ActionType.COPY_AND_LAUNCH) }.forEach { action ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.actionType == action, enabled = !working, onClick = { dirty = true; vm.change { it.copy(actionType = action) } })
                        TextButton(enabled = !working, onClick = { dirty = true; vm.change { it.copy(actionType = action) } }) { Text(action.label()) }
                    }
                }
                if (needsApp) {
                    val app = state.apps.find { it.packageName == state.target }
                    Text(if (state.actionType == ActionType.SHARE) "目标应用（可选）" else "目标应用 *", style = MaterialTheme.typography.titleSmall)
                    OutlinedCard(onClick = { vm.loadApps(); chooseApp = true }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        ListItem(leadingContent = { ApplicationIcon(app) },
                            headlineContent = { Text(app?.name ?: if (state.target.isBlank()) { if (state.actionType == ActionType.SHARE) "系统分享面板" else "选择应用" } else if (state.appsLoading) "正在读取应用信息…" else "原目标应用当前不可用") },
                            supportingContent = { Text(if (state.target.isBlank()) "从已安装应用中选择或搜索" else "点击更换应用") })
                    }
                    if (state.actionType == ActionType.SHARE && state.target.isNotBlank()) {
                        TextButton(enabled = !working, onClick = { dirty = true; vm.change { it.copy(target = "") } }) { Text("改用系统分享面板") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (chooseApp) AppPicker(state.apps, state.appsLoading, state.appsError, state.target,
        onReload = { vm.loadApps(force = true) },
        onSelect = { dirty = true; vm.selectApp(it); chooseApp = false }, onDismiss = { chooseApp = false })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
