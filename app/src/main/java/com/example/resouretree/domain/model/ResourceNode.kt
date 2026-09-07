package com.example.resouretree.domain.model

enum class NodeType { FOLDER, ITEM }
enum class ActionType { NONE, COPY, LAUNCH_APP, COPY_AND_LAUNCH }

data class ResourceAction(
    val type: ActionType = ActionType.NONE,
    val text: String = "",
    val packageName: String = ""
)

data class ResourceNode(
    val id: String,
    val parentId: String?,
    val type: NodeType,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val action: ResourceAction = ResourceAction(),
    val isPinned: Boolean = false
)

object Tree {
    val order = compareByDescending<ResourceNode> { it.isPinned }.thenBy { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id }

    fun descendants(nodes: List<ResourceNode>, id: String): Set<String> {
        val children = nodes.groupBy { it.parentId }
        val found = linkedSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(id)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (found.add(current)) children[current].orEmpty().forEach { pending.add(it.id) }
        }
        return found
    }

    fun breadcrumb(nodes: List<ResourceNode>, id: String?): List<ResourceNode> {
        val byId = nodes.associateBy { it.id }
        val path = mutableListOf<ResourceNode>()
        val visited = mutableSetOf<String>()
        var current = id
        while (current != null) {
            require(visited.add(current)) { "目录关系存在循环" }
            val node = requireNotNull(byId[current]) { "目录不存在" }
            path.add(node)
            current = node.parentId
        }
        return path.reversed()
    }

    fun validate(nodes: List<ResourceNode>) {
        val byId = nodes.associateBy { it.id }
        require(byId.size == nodes.size) { "节点 ID 重复" }
        nodes.forEach { node ->
            require(node.name.isNotBlank()) { "名称不能为空" }
            if (node.parentId != null) require(byId[node.parentId]?.type == NodeType.FOLDER) { "父目录不存在或不是文件夹" }
            val visited = mutableSetOf<String>()
            var current: ResourceNode? = node
            while (current != null) {
                require(visited.add(current.id)) { "目录关系存在循环" }
                require(visited.size <= 100) { "目录层级不能超过 100 层" }
                current = current.parentId?.let(byId::get)
            }
        }
    }
}
