package com.example.resouretree.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.action.*
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrowserState(
    val loading: Boolean = true, val error: String? = null, val busy: Boolean = false,
    val currentId: String? = null, val all: List<ResourceNode> = emptyList(),
    val children: List<ResourceNode> = emptyList(), val breadcrumb: List<ResourceNode> = emptyList(),
    val query: String = "", val results: List<ResourceNode> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BrowserViewModel(
    private val repository: NodeRepository,
    private val documents: DocumentStore,
    private val executor: ActionExecutor,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val current = savedState.getStateFlow<String?>("currentDirectory", null)
    private val query = savedState.getStateFlow("searchQuery", "")
    private val ready = MutableStateFlow(false)
    private val failure = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    private val nodes = repository.all.catch { failure.value = it.message ?: "读取数据库失败" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val search = query.flatMapLatest { repository.search(it.trim()) }
        .catch { failure.value = it.message ?: "搜索失败" }
    private val base = combine(nodes, current, query, search) { all, id, text, results ->
        val validId = id?.takeIf { target -> all.any { it.id == target && it.type == NodeType.FOLDER } }
        BrowserState(currentId = validId, all = all,
            children = all.filter { it.parentId == validId }.sortedWith(Tree.order),
            breadcrumb = Tree.breadcrumb(all, validId), query = text, results = results)
    }
    val state = combine(base, ready, failure, busy) { base, ready, error, busy ->
        base.copy(loading = !ready, error = error, busy = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowserState())

    init { initialize() }
    fun initialize() {
        failure.value = null
        viewModelScope.launch {
            try { repository.initialize(); ready.value = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure.value = e.message ?: "数据库初始化失败" }
        }
    }
    fun open(id: String?) { savedState["currentDirectory"] = id }
    fun up() { open(state.value.breadcrumb.dropLast(1).lastOrNull()?.id) }
    fun search(text: String) { savedState["searchQuery"] = text }
    fun execute(node: ResourceNode) {
        val result = executor.execute(node.content, node.action)
        if (result is ActionResult.Error) notify(result.message)
    }
    fun notify(message: String) { viewModelScope.launch { messagesChannel.send(message) } }
    private fun operation(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { messagesChannel.send(e.message ?: "操作失败，请重试") }
            finally { busy.value = false }
        }
    }
    fun delete(node: ResourceNode) = operation { repository.delete(node.id) }
    fun setPinned(node: ResourceNode) = operation {
        repository.setPinned(node.id, !node.isPinned)
    }
    fun reorder(parentId: String?, ids: List<String>) = operation { repository.reorder(parentId, ids) }
    fun move(node: ResourceNode, parentId: String?) = operation { repository.move(node.id, parentId) }
    fun deleteMany(ids: Set<String>, onSuccess: () -> Unit) = operation(onSuccess) {
        repository.deleteMany(ids)
    }
    fun transferMany(ids: Set<String>, parentId: String?, copy: Boolean, onSuccess: () -> Unit) = operation(onSuccess) {
        if (copy) repository.copyMany(ids, parentId) else repository.moveMany(ids, parentId)
    }
    fun importDocument(uri: Uri) = operation { repository.importJson(documents.read(uri)) }
    fun exportDocument(uri: Uri) = operation { documents.write(uri, repository.exportJson()) }
}
