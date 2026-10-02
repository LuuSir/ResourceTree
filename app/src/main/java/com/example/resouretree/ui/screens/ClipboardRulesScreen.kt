package com.example.resouretree.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.resouretree.data.clipboard.ClipboardRuleStore
import com.example.resouretree.domain.model.ClipboardRule
import com.example.resouretree.domain.model.ActionType
import com.example.resouretree.domain.model.ClipboardMatchType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardRulesScreen(store: ClipboardRuleStore, onBack: () -> Unit) {
    val rules by store.rules.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    if (editingId != null) {
        key(editingId) {
            ClipboardRuleEditor(rules.find { it.id == editingId } ?: ClipboardRule(editingId!!, "", targets = emptyList()),
                onSave = { store.save(it); editingId = null }, onBack = { editingId = null })
        }
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("剪贴板规则") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            actions = { TextButton(onClick = { editingId = UUID.randomUUID().toString() }) { Text("添加规则") } })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("回到应用时按规则预填新条目，确认保存后放入首页。多条匹配时，固定字符更多的优先；相同时优先前缀匹配，再按列表顺序。", style = MaterialTheme.typography.bodyMedium) }
            items(rules, key = { it.id }) { rule ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(rule.prefix, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(rule.name.ifBlank { "名称取自剪贴板首行" }) },
                        trailingContent = { Switch(checked = rule.enabled, onCheckedChange = { store.save(rule.copy(enabled = it)) }, modifier = Modifier.testTag("rule-enabled-${rule.id}")) })
                    Text("${if (rule.matchType == ClipboardMatchType.PREFIX) "前缀匹配" else "通配符匹配"} · ${rule.actionType.label()}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    if (rule.actionType != ActionType.OPEN_WEBVIEW) Text(rule.targets.joinToString("\n"), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingId = rule.id }, modifier = Modifier.testTag("rule-edit-${rule.id}")) { Text("编辑") }
                        TextButton(onClick = { deletingId = rule.id }, modifier = Modifier.testTag("rule-delete-${rule.id}")) { Text("删除") }
                    }
                }
            }
            if (rules.isEmpty()) item { Text("暂无规则，剪贴板不会自动打开新建条目。") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("删除这条规则？") },
            text = { Text("已保存的条目不受影响。") },
            confirmButton = { TextButton(onClick = { store.delete(id); deletingId = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardRuleEditor(rule: ClipboardRule, onSave: (ClipboardRule) -> Unit, onBack: () -> Unit) {
    var prefix by rememberSaveable { mutableStateOf(rule.prefix) }
    var name by rememberSaveable { mutableStateOf(rule.name) }
    var targets by rememberSaveable { mutableStateOf(rule.targets.joinToString("\n")) }
    var action by rememberSaveable { mutableStateOf(rule.actionType) }
    var matchType by rememberSaveable { mutableStateOf(rule.matchType) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val dirty = prefix != rule.prefix || name != rule.name || targets != rule.targets.joinToString("\n") || action != rule.actionType || matchType != rule.matchType
    val back = { if (dirty) discard = true else onBack() }
    BackHandler { back() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("编辑剪贴板规则") }, navigationIcon = { TextButton(onClick = back) { Text("返回") } },
            actions = { TextButton(onClick = {
                try { onSave(rule.copy(prefix = prefix, name = name, targets = targets.lines(), actionType = action, matchType = matchType).validated()) }
                catch (e: IllegalArgumentException) { error = e.message }
            }) { Text("保存规则") } })
    }) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClipboardMatchType.entries.forEach { type ->
                    FilterChip(selected = matchType == type, onClick = { matchType = type; error = null },
                        label = { Text(if (type == ClipboardMatchType.PREFIX) "前缀匹配" else "通配符匹配") })
                }
            }
            OutlinedTextField(prefix, { prefix = it; error = null }, label = { Text(if (matchType == ClipboardMatchType.PREFIX) "匹配前缀 *" else "通配符规则 *") }, singleLine = true,
                supportingText = { Text(if (matchType == ClipboardMatchType.PREFIX) "区分大小写，忽略剪贴板开头的空白"
                    else "* 匹配任意长度（含换行），? 匹配一个字符。匹配整段文字，区分大小写，忽略开头空白。例：*https://v.douyin.com/*") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it; error = null }, label = { Text("默认名称（可选）") }, singleLine = true,
                supportingText = { Text("留空时使用剪贴板的第一行") }, modifier = Modifier.fillMaxWidth())
            Text("预填动作", style = MaterialTheme.typography.titleSmall)
            listOf(ActionType.COPY_AND_LAUNCH, ActionType.OPEN_WEBVIEW).forEach { type ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = action == type, onClick = { action = type; error = null })
                    TextButton(onClick = { action = type; error = null }) { Text(type.label()) }
                }
            }
            if (action != ActionType.OPEN_WEBVIEW) OutlinedTextField(targets, { targets = it; error = null }, label = { Text("目标包名 / 候选包名 *") }, minLines = 3,
                supportingText = { Text("每行一个，按从上到下的顺序使用首个可打开的应用；均未安装时仍预填第一个包名。") }, modifier = Modifier.fillMaxWidth())
            Text(if (action == ActionType.OPEN_WEBVIEW) "无需目标应用。可用 https:// 或具体网站地址作为前缀，剪贴板内容需为完整网址；确认保存到首页后点击条目打开网页。"
                else "预填完整剪贴板文字，保存位置为首页。设置包名后，无需打开应用列表。", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(onClick = onBack) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}
