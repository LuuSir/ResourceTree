package com.example.resouretree.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resouretree.data.local.entity.TagsCodec
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val loading: Boolean = true, val saving: Boolean = false, val saved: Boolean = false,
    val error: String? = null, val loadFailed: Boolean = false, val type: NodeType = NodeType.ITEM,
    val name: String = "", val content: String = "", val tags: String = "",
    val actionType: ActionType = ActionType.COPY, val actionText: String = "", val packageName: String = ""
)

class EditorViewModel(
    private val repository: NodeRepository, private val handle: SavedStateHandle,
    private val id: String?, private val parentId: String?, type: NodeType
) : ViewModel() {
    private var original: ResourceNode? = null
    private val mutable = MutableStateFlow(EditorState(type = type))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                original = id?.let { requireNotNull(repository.node(it)) { "条目已被删除" } }
                val node = original
                mutable.value = EditorState(loading = false, type = node?.type ?: type,
                    name = handle["name"] ?: node?.name.orEmpty(), content = handle["content"] ?: node?.content.orEmpty(),
                    tags = handle["tags"] ?: node?.tags?.joinToString(", ").orEmpty(),
                    actionType = handle.get<String>("actionType")?.let(ActionType::valueOf) ?: node?.action?.type ?: ActionType.COPY,
                    actionText = handle["actionText"] ?: node?.action?.text.orEmpty(),
                    packageName = handle["packageName"] ?: node?.action?.packageName.orEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, loadFailed = true, error = e.message) }
        }
    }
    fun change(transform: (EditorState) -> EditorState) {
        val next = transform(mutable.value).copy(error = null)
        mutable.value = next
        handle["name"] = next.name; handle["content"] = next.content; handle["tags"] = next.tags
        handle["actionType"] = next.actionType.name; handle["actionText"] = next.actionText; handle["packageName"] = next.packageName
    }
    fun save() {
        val form = mutable.value
        if (form.loading || form.saving || form.saved || form.loadFailed) return
        if (form.name.isBlank()) { mutable.value = form.copy(error = "请输入名称"); return }
        val launch = form.type == NodeType.ITEM && form.actionType in listOf(ActionType.LAUNCH_APP, ActionType.COPY_AND_LAUNCH)
        if (launch && !Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(form.packageName.trim())) {
            mutable.value = form.copy(error = "请输入有效的目标 App package，例如 tv.danmaku.bili"); return
        }
        mutable.value = form.copy(saving = true)
        viewModelScope.launch {
            try {
                val node = (original ?: ResourceNode(UUID.randomUUID().toString(), parentId, form.type, form.name)).copy(
                    name = form.name.trim(), content = if (form.type == NodeType.ITEM) form.content else "",
                    tags = if (form.type == NodeType.ITEM) TagsCodec.parseInput(form.tags) else emptyList(),
                    action = if (form.type == NodeType.ITEM) ResourceAction(form.actionType, form.actionText, form.packageName.trim()) else ResourceAction())
                repository.save(node, isNew = id == null)
                mutable.value = mutable.value.copy(saving = false, saved = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(saving = false, error = e.message ?: "保存失败") }
        }
    }
}
