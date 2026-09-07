package com.example.resouretree.ui.screens

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

fun ActionType.label(): String = when (this) {
    ActionType.NONE -> "无动作"; ActionType.COPY -> "复制文字"
    ActionType.LAUNCH_APP -> "打开 App"; ActionType.COPY_AND_LAUNCH -> "复制并打开 App"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, editing: Boolean, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dirty by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val back = { if (dirty && !state.saved) discard = true else onBack() }
    BackHandler(enabled = !state.saved) { if (!state.saving) back() }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("${if (editing) "编辑" else "新建"}${if (state.type == NodeType.FOLDER) "文件夹" else "条目"}") },
            navigationIcon = { TextButton(onClick = back, enabled = !state.saving) { Text("返回") } },
            actions = { TextButton(onClick = vm::save, enabled = !state.loading && !state.saving && !state.loadFailed) { Text(if (state.saving) "保存中" else "保存") } })
    }) { padding ->
        if (state.loading) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        else Column(Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(state.name, { dirty = true; vm.change { s -> s.copy(name = it) } }, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving && !state.loadFailed)
            if (state.type == NodeType.ITEM && !state.loadFailed) {
                OutlinedTextField(state.content, { value ->
                    dirty = true; vm.change { s -> s.copy(content = value, actionText = if (s.actionText == s.content) value else s.actionText) }
                }, label = { Text("内容") }, minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                OutlinedTextField(state.tags, { dirty = true; vm.change { s -> s.copy(tags = it) } }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                Text("点击条目时", style = MaterialTheme.typography.titleSmall)
                ActionType.entries.forEach { action ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.actionType == action, enabled = !state.saving, onClick = { dirty = true; vm.change { it.copy(actionType = action) } })
                        TextButton(enabled = !state.saving, onClick = { dirty = true; vm.change { it.copy(actionType = action) } }) { Text(action.label()) }
                    }
                }
                if (state.actionType == ActionType.COPY || state.actionType == ActionType.COPY_AND_LAUNCH) {
                    OutlinedTextField(state.actionText, { dirty = true; vm.change { s -> s.copy(actionText = it) } }, label = { Text("动作复制文本") }, supportingText = { Text("默认跟随内容，可单独修改") }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                }
                if (state.actionType == ActionType.LAUNCH_APP || state.actionType == ActionType.COPY_AND_LAUNCH) {
                    OutlinedTextField(state.packageName, { dirty = true; vm.change { s -> s.copy(packageName = it) } }, label = { Text("目标 App package *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.saving)
                    Text("示例包名", style = MaterialTheme.typography.labelMedium)
                    listOf("tv.danmaku.bili", "com.bilibili.app.in").forEach { sample ->
                        OutlinedButton(enabled = !state.saving, onClick = { dirty = true; vm.change { it.copy(packageName = sample) } }) { Text(sample) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
