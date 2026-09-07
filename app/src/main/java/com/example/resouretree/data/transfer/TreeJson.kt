package com.example.resouretree.data.transfer

import com.example.resouretree.domain.model.*
import kotlinx.serialization.json.*
import java.util.UUID

data class ExportFileDto(val schemaVersion: Int = 1, val roots: List<ExportNodeDto>)
data class ExportActionDto(val type: String, val text: String, val packageName: String)
data class ExportNodeDto(
    val id: String?, val type: String, val name: String, val sortOrder: Int,
    val createdAt: Long, val updatedAt: Long, val content: String = "",
    val tags: List<String> = emptyList(), val action: ExportActionDto? = null,
    val children: List<ExportNodeDto> = emptyList(), val isPinned: Boolean = false
)

/** Public schema is deliberately independent of Room's storage representation. */
class TreeJson {
    companion object { const val MAX_BYTES = 10 * 1024 * 1024; const val MAX_NODES = 10000; const val MAX_DEPTH = 100 }
    private val json = Json { prettyPrint = true; isLenient = false }

    fun toDto(nodes: List<ResourceNode>): ExportFileDto {
        Tree.validate(nodes)
        val groups = nodes.groupBy { it.parentId }
        fun branch(node: ResourceNode): ExportNodeDto = ExportNodeDto(
            node.id, node.type.name.lowercase(), node.name, node.sortOrder, node.createdAt, node.updatedAt,
            node.content, node.tags, if (node.type == NodeType.ITEM) ExportActionDto(
                node.action.type.name, node.action.text, node.action.packageName) else null,
            groups[node.id].orEmpty().sortedWith(Tree.order).map { branch(it) }, node.isPinned
        )
        return ExportFileDto(roots = groups[null].orEmpty().sortedWith(Tree.order).map { branch(it) })
    }

    fun encode(file: ExportFileDto): String {
        fun branch(node: ExportNodeDto): JsonObject = buildJsonObject {
            node.id?.let { put("id", it) }
            put("type", node.type); put("name", node.name); put("sortOrder", node.sortOrder)
            put("createdAt", node.createdAt); put("updatedAt", node.updatedAt)
            put("isPinned", node.isPinned)
            if (node.type == "folder") put("children", JsonArray(node.children.map { branch(it) }))
            else {
                put("content", node.content)
                put("tags", JsonArray(node.tags.map(::JsonPrimitive)))
                node.action?.let { a -> put("action", buildJsonObject {
                    put("type", a.type); put("text", a.text); put("packageName", a.packageName)
                }) }
            }
        }
        val root = buildJsonObject {
            put("schemaVersion", file.schemaVersion); put("roots", JsonArray(file.roots.map { branch(it) }))
        }
        return json.encodeToString(JsonElement.serializer(), root)
    }

    fun decode(text: String): ExportFileDto {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件超过 10 MB，无法导入" }
        // Bound nesting before parsing, so malicious input cannot exhaust the parser stack.
        var depth = 0; var quoted = false; var escaped = false
        text.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '[', '{' -> { depth++; require(depth <= MAX_DEPTH * 2 + 10) { "JSON 嵌套过深" } }
                ']', '}' -> depth--
            }
        }
        val root = try { json.parseToJsonElement(text.removePrefix("\uFEFF")) as? JsonObject }
        catch (e: IllegalArgumentException) { throw IllegalArgumentException("非法 JSON：${e.message?.take(180)}", e) }
        requireNotNull(root) { "JSON 顶层必须是对象" }
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?: throw IllegalArgumentException("缺少或无效的 schemaVersion")
        require(version <= 1) { "该文件来自更新版本的 ResourceTree，当前版本无法导入。" }
        require(version == 1) { "不支持的 schemaVersion：$version" }
        var count = 0
        val now = System.currentTimeMillis()
        fun branch(element: JsonElement, level: Int, position: Int): ExportNodeDto {
            require(level <= MAX_DEPTH) { "目录层级不能超过 100 层" }
            require(++count <= MAX_NODES) { "一次最多导入 10000 个节点" }
            val obj = element as? JsonObject ?: throw IllegalArgumentException("节点必须是对象")
            val type = obj.string("type", required = true)
            require(type == "folder" || type == "item") { "不支持的节点类型：$type" }
            val name = obj.string("name", required = true).trim()
            require(name.isNotBlank()) { "节点名称不能为空" }
            val children = obj.array("children", required = false)
            require(type == "folder" || children.isEmpty()) { "条目不能包含 children：$name" }
            val tags = obj.array("tags", required = false).map {
                require(it is JsonPrimitive && it.isString) { "tags 必须为字符串数组：$name" }
                it.content
            }
            val content = obj.string("content")
            val action = obj["action"]?.takeUnless { it == JsonNull }?.let {
                require(it is JsonObject) { "action 必须为对象：$name" }
                val actionType = it.string("type", required = true)
                require(ActionType.entries.any { a -> a.name == actionType }) { "不支持的动作：$actionType" }
                val packageName = it.string("packageName")
                if (actionType == "LAUNCH_APP" || actionType == "COPY_AND_LAUNCH")
                    require(packageName.isNotBlank()) { "启动应用的动作缺少 packageName：$name" }
                ExportActionDto(actionType, if (it.containsKey("text")) it.string("text") else content, packageName)
            }
            val created = obj.number("createdAt", now)
            val pinned = obj["isPinned"]?.let {
                require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "isPinned 必须为布尔值" }
                it.boolean
            } ?: false
            return ExportNodeDto(obj.string("id").takeIf(String::isNotBlank), type, name,
                obj.number("sortOrder", position.toLong()).also { require(it in 0..Int.MAX_VALUE.toLong()) { "sortOrder 超出范围" } }.toInt(),
                created, obj.number("updatedAt", created), content, tags, action,
                children.mapIndexed { index, child -> branch(child, level + 1, index) }, pinned)
        }
        return ExportFileDto(roots = root.array("roots", required = true).mapIndexed { i, e -> branch(e, 1, i) })
    }

    fun toNodes(file: ExportFileDto, existingIds: Set<String> = emptySet()): List<ResourceNode> {
        require(file.schemaVersion == 1) { "不支持的 schemaVersion" }
        val used = existingIds.toMutableSet()
        val result = mutableListOf<ResourceNode>()
        fun branch(dto: ExportNodeDto, parentId: String?) {
            val original = dto.id?.takeIf { candidate ->
                runCatching { UUID.fromString(candidate).toString().equals(candidate, ignoreCase = true) }.getOrDefault(false)
            }?.lowercase()
            var id = original ?: UUID.randomUUID().toString()
            while (!used.add(id)) id = UUID.randomUUID().toString()
            result += ResourceNode(id, parentId, NodeType.valueOf(dto.type.uppercase()), dto.name,
                dto.sortOrder, dto.createdAt, dto.updatedAt, dto.content, dto.tags,
                dto.action?.let { ResourceAction(ActionType.valueOf(it.type), it.text, it.packageName) } ?: ResourceAction(), dto.isPinned)
            dto.children.forEach { branch(it, id) }
        }
        file.roots.forEach { branch(it, null) }
        Tree.validate(result)
        return result
    }

    private fun JsonObject.string(key: String, required: Boolean = false): String {
        val value = this[key]
        if (value == null || value == JsonNull) {
            require(!required) { "缺少字段：$key" }; return ""
        }
        require(value is JsonPrimitive && value.isString) { "$key 必须为字符串" }
        return value.content
    }
    private fun JsonObject.array(key: String, required: Boolean): JsonArray {
        val value = this[key]
        if (value == null) { require(!required) { "缺少字段：$key" }; return JsonArray(emptyList()) }
        require(value is JsonArray) { "$key 必须为数组" }
        return value
    }
    private fun JsonObject.number(key: String, default: Long): Long {
        val value = this[key] ?: return default
        require(value is JsonPrimitive && !value.isString && value.longOrNull != null) { "$key 必须为整数" }
        return requireNotNull(value.longOrNull).also { require(it >= 0) { "$key 不能为负数" } }
    }
}
