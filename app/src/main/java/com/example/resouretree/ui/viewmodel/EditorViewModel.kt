package com.example.resouretree.ui.viewmodel

import android.net.Uri
import com.example.resouretree.data.transfer.DocumentStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.resouretree.data.local.entity.TagsCodec
import com.example.resouretree.data.apps.AppCatalog
import com.example.resouretree.data.apps.InstalledApp
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
    val name: String = "", val content: ResourceContent = ResourceContent(), val tags: String = "",
    val importingMedia: Boolean = false,
    val actionType: ActionType = ActionType.COPY, val target: String = "",
    val targetApp: InstalledApp? = null, val targetLoading: Boolean = false,
    val apps: List<InstalledApp> = emptyList(), val appsLoading: Boolean = false, val appsError: String? = null
)

class EditorViewModel(
    private val repository: NodeRepository, private val handle: SavedStateHandle,
    private val id: String?, private val parentId: String?, type: NodeType, private val appCatalog: AppCatalog, private val documents: DocumentStore
) : ViewModel() {
    private var original: ResourceNode? = null
    private val mutable = MutableStateFlow(EditorState(type = type))
    val state = mutable.asStateFlow()
    private var appsLoaded = false
    init {
        viewModelScope.launch {
            try {
                original = id?.let { requireNotNull(repository.node(it)) { "条目已被删除" } }
                val node = original
                mutable.value = EditorState(loading = false, type = node?.type ?: type,
                    name = handle["name"] ?: node?.name.orEmpty(), content = ResourceContent(
                        type = handle.get<String>("contentType")?.let(ContentType::valueOf) ?: node?.content?.type ?: ContentType.TEXT,
                        text = handle["contentText"] ?: node?.content?.text.orEmpty(),
                        path = handle["contentPath"] ?: node?.content?.path.orEmpty(),
                        mimeType = handle["contentMimeType"] ?: node?.content?.mimeType.orEmpty()),
                    tags = handle["tags"] ?: node?.tags?.joinToString(", ").orEmpty(),
                    actionType = handle.get<String>("actionType")?.let(ActionType::valueOf) ?: node?.action?.type ?: ActionType.COPY,
                    target = handle["target"] ?: node?.action?.target.orEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(loading = false, loadFailed = true, error = e.message) }
        }
    }
    fun change(transform: (EditorState) -> EditorState) {
        val next = transform(mutable.value).copy(error = null)
        mutable.value = next
        handle["name"] = next.name; handle["contentType"] = next.content.type.name; handle["contentText"] = next.content.text;
        handle["contentPath"] = next.content.path; handle["contentMimeType"] = next.content.mimeType; handle["tags"] = next.tags
        handle["actionType"] = next.actionType.name; handle["target"] = next.target
    }
    fun loadApps(force: Boolean = false) {
        if (mutable.value.appsLoading || (appsLoaded && !force)) return
        mutable.value = mutable.value.copy(appsLoading = true, appsError = null)
        viewModelScope.launch {
            try {
                val apps = appCatalog.load()
                appsLoaded = true
                mutable.value = mutable.value.copy(apps = apps, appsLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.value = mutable.value.copy(appsLoading = false, appsError = "无法读取应用列表。请允许系统的应用列表访问提示，然后重试。")
            }
        }
    }
    suspend fun loadTarget() {
        val target = state.value.target
        mutable.value = mutable.value.copy(targetApp = null, targetLoading = target.isNotBlank())
        if (target.isBlank()) return
        try {
            val app = state.value.apps.find { it.packageName == target } ?: appCatalog.find(target)
            if (state.value.target == target) mutable.value = mutable.value.copy(targetApp = app)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The configured package remains usable even if its label is unavailable. */ }
        finally { if (state.value.target == target) mutable.value = mutable.value.copy(targetLoading = false) }
    }
    fun selectApp(app: InstalledApp) { change { it.copy(target = app.packageName) } }
    fun selectContentType(type: ContentType) {
        if (type == state.value.content.type) return
        change { it.copy(content = ResourceContent(type),
            actionType = if (type != ContentType.TEXT && it.actionType in listOf(ActionType.COPY, ActionType.COPY_AND_LAUNCH)) ActionType.SHARE else it.actionType) }
    }
    fun selectMedia(uri: Uri) {
        val type = state.value.content.type
        if (state.value.importingMedia || type == ContentType.TEXT) return
        mutable.value = mutable.value.copy(importingMedia = true, error = null)
        viewModelScope.launch {
            try { val content = documents.importMedia(uri, type); change { it.copy(content = content) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "读取文件失败") }
            finally { mutable.value = mutable.value.copy(importingMedia = false) }
        }
    }
    fun mediaError() { mutable.value = mutable.value.copy(error = "无法打开系统文件选择器") }
    fun save() {
        val form = mutable.value
        if (form.loading || form.saving || form.importingMedia || form.saved || form.loadFailed) return
        if (form.name.isBlank()) { mutable.value = form.copy(error = "请输入名称"); return }
        val launch = form.type == NodeType.ITEM && form.actionType in listOf(ActionType.LAUNCH_APP, ActionType.COPY_AND_LAUNCH)
        if (launch && !Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(form.target.trim())) {
            mutable.value = form.copy(error = "请选择目标应用"); return
        }
        if (form.type == NodeType.ITEM && form.content.type != ContentType.TEXT) {
            if (form.content.path.isBlank() || !validMime(form.content.type, form.content.mimeType)) {
                mutable.value = form.copy(error = "请选择媒体文件并填写有效的 MIME 类型"); return
            }
            if (form.actionType in listOf(ActionType.COPY, ActionType.COPY_AND_LAUNCH)) {
                mutable.value = form.copy(error = "媒体内容请使用分享动作"); return
            }
        }
        mutable.value = form.copy(saving = true)
        viewModelScope.launch {
            try {
                val node = (original ?: ResourceNode(UUID.randomUUID().toString(), parentId, form.type, form.name)).copy(
                    name = form.name.trim(), content = if (form.type == NodeType.ITEM) form.content else ResourceContent(),
                    tags = if (form.type == NodeType.ITEM) TagsCodec.parseInput(form.tags) else emptyList(),
                    action = if (form.type == NodeType.ITEM) ResourceAction(form.actionType, if (form.actionType in listOf(ActionType.LAUNCH_APP, ActionType.COPY_AND_LAUNCH, ActionType.SHARE)) form.target.trim() else "") else ResourceAction())
                repository.save(node, isNew = id == null)
                mutable.value = mutable.value.copy(saving = false, saved = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(saving = false, error = e.message ?: "保存失败") }
        }
    }
}
