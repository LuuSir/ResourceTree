package com.example.resouretree.data.repository

import androidx.room.withTransaction
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.local.entity.*
import com.example.resouretree.data.transfer.TreeJson
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class NodeRepository(private val database: ResourceDatabase, private val codec: TreeJson = TreeJson()) {
    private val dao = database.nodes()
    val all = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    fun children(parentId: String?) = dao.getChildren(parentId).map { rows -> rows.map { it.toDomain() } }
    fun search(query: String) = dao.searchItems(query).map { rows -> rows.map { it.toDomain() } }
    suspend fun node(id: String) = dao.getNode(id)?.toDomain()

    suspend fun initialize() = database.withTransaction {
        if (dao.metadata("demo_initialized") == null) {
            if (dao.getAll().isEmpty()) {
                val folder = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "哔哩哔哩")
                val example = ResourceNode(UUID.randomUUID().toString(), folder.id, NodeType.FOLDER, "示例")
                val item = ResourceNode(UUID.randomUUID().toString(), example.id, NodeType.ITEM, "Bilibili 示例",
                    content = "BV1Futr6xEkb", tags = listOf("示例", "三国"),
                    action = ResourceAction(ActionType.COPY_AND_LAUNCH, "BV1Futr6xEkb", "tv.danmaku.bili"))
                dao.insertNodes(listOf(folder, example, item).map { it.toEntity() })
            }
            dao.setMetadata(MetadataEntity("demo_initialized", "true"))
        }
    }

    suspend fun save(node: ResourceNode, isNew: Boolean) = database.withTransaction {
        require(node.name.isNotBlank()) { "名称不能为空" }
        if (node.parentId != null) require(dao.getNode(node.parentId)?.type == NodeType.FOLDER.name) { "父目录已被删除" }
        val nodes = dao.getAll().map { it.toDomain() }
        Tree.validate(nodes.filterNot { it.id == node.id } + node)
        if (isNew) {
            val nextOrder = (nodes.filter { it.parentId == node.parentId }.maxOfOrNull { it.sortOrder } ?: -1).toLong() + 1
            dao.insertNode(node.copy(sortOrder = nextOrder.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toEntity())
        } else {
            require(dao.getNode(node.id) != null) { "此节点已被删除" }
            dao.updateNode(node.copy(updatedAt = System.currentTimeMillis()).toEntity())
        }
    }

    suspend fun delete(id: String) { deleteMany(setOf(id)) }

    suspend fun move(id: String, parentId: String?) { moveMany(setOf(id), parentId) }

    suspend fun setPinned(id: String, pinned: Boolean) = database.withTransaction {
        val node = requireNotNull(dao.getNode(id)) { "节点已被删除" }.toDomain()
        val siblings = dao.getAll().map { it.toDomain() }.filter { it.parentId == node.parentId && it.id != id }.sortedWith(Tree.order)
        val ordered = if (pinned) listOf(node.copy(isPinned = true)) + siblings
            else siblings.filter { it.isPinned } + node.copy(isPinned = false) + siblings.filterNot { it.isPinned }
        val now = System.currentTimeMillis()
        ordered.forEachIndexed { index, item -> dao.updateNode(item.copy(sortOrder = index, updatedAt = now).toEntity()) }
    }

    suspend fun reorder(parentId: String?, orderedIds: List<String>) = database.withTransaction {
        val siblings = dao.getAll().filter { it.parentId == parentId }.map { it.toDomain() }
        require(orderedIds.size == orderedIds.toSet().size && orderedIds.toSet() == siblings.map { it.id }.toSet()) {
            "目录内容已变化，请重新拖动排序"
        }
        val byId = siblings.associateBy { it.id }
        val ordered = orderedIds.map { requireNotNull(byId[it]) }
        require(ordered.zipWithNext().none { (a, b) -> !a.isPinned && b.isPinned }) { "置顶条目需要保留在列表顶部" }
        val now = System.currentTimeMillis()
        ordered.forEachIndexed { index, node ->
            if (node.sortOrder != index) dao.updateNode(node.copy(sortOrder = index, updatedAt = now).toEntity())
        }
    }

    // If both an ancestor and a descendant are selected, process that subtree once.
    private fun selectionRoots(nodes: List<ResourceNode>, ids: Set<String>): List<ResourceNode> {
        require(ids.isNotEmpty()) { "请先选择节点" }
        val byId = nodes.associateBy { it.id }
        require(ids.all(byId::containsKey)) { "部分所选节点已被删除，请重新选择" }
        return nodes.filter { node ->
            node.id in ids && Tree.breadcrumb(nodes, node.parentId).none { it.id in ids }
        }.sortedWith(Tree.order)
    }

    suspend fun deleteMany(ids: Set<String>): Int = database.withTransaction {
        val nodes = dao.getAll().map { it.toDomain() }
        val roots = selectionRoots(nodes, ids)
        val deleted = roots.flatMap { Tree.descendants(nodes, it.id) }.toSet()
        // Reverse breadth-first order removes children before parents; FK CASCADE is a second safeguard.
        roots.forEach { root -> Tree.descendants(nodes, root.id).toList().asReversed().forEach { dao.deleteNode(it) } }
        deleted.size
    }

    suspend fun moveMany(ids: Set<String>, parentId: String?): Int = database.withTransaction {
        val nodes = dao.getAll().map { it.toDomain() }
        val roots = selectionRoots(nodes, ids)
        require(roots.none { parentId in Tree.descendants(nodes, it.id) }) { "不能移动到所选文件夹自身或子目录" }
        val next = (nodes.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: -1).toLong() + 1
        val now = System.currentTimeMillis()
        val moved = roots.mapIndexed { index, node -> node.copy(parentId = parentId,
            sortOrder = (next + index).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), updatedAt = now) }
        val movedIds = moved.map { it.id }.toSet()
        Tree.validate(nodes.filterNot { it.id in movedIds } + moved)
        moved.forEach { dao.updateNode(it.toEntity()) }
        roots.size
    }

    suspend fun copyMany(ids: Set<String>, parentId: String?): Int = database.withTransaction {
        val nodes = dao.getAll().map { it.toDomain() }
        val roots = selectionRoots(nodes, ids)
        if (parentId != null) require(nodes.any { it.id == parentId && it.type == NodeType.FOLDER }) { "目标目录不存在" }
        val groups = nodes.groupBy { it.parentId }
        val usedNames = groups[parentId].orEmpty().map { it.name }.toMutableSet()
        val next = (groups[parentId].orEmpty().maxOfOrNull { it.sortOrder } ?: -1).toLong() + 1
        val now = System.currentTimeMillis()
        val copies = mutableListOf<ResourceNode>()
        roots.forEachIndexed { index, root ->
            var name = root.name
            var suffix = 1
            while (!usedNames.add(name)) {
                name = root.name + if (suffix == 1) "（副本）" else "（副本 $suffix）"
                suffix++
            }
            val pending = ArrayDeque<Pair<ResourceNode, String?>>()
            pending.add(root to parentId)
            // Read only the original snapshot, so copying into a descendant cannot recurse into new copies.
            while (pending.isNotEmpty()) {
                val (source, newParent) = pending.removeFirst()
                val copy = source.copy(id = UUID.randomUUID().toString(), parentId = newParent,
                    name = if (source.id == root.id) name else source.name,
                    sortOrder = if (source.id == root.id) (next + index).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else source.sortOrder,
                    createdAt = now, updatedAt = now)
                copies.add(copy)
                groups[source.id].orEmpty().sortedWith(Tree.order).forEach { pending.add(it to copy.id) }
            }
        }
        Tree.validate(nodes + copies)
        dao.insertNodes(copies.map { it.toEntity() })
        roots.size
    }

    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        val nodes = database.withTransaction { dao.getAll().map { it.toDomain() } }
        codec.encode(codec.toDto(nodes))
    }

    suspend fun importJson(text: String): Int = withContext(Dispatchers.Default) {
        val dto = codec.decode(text)
        database.withTransaction {
            val existing = dao.getAll()
            val incoming = codec.toNodes(dto, existing.map { it.id }.toSet())
            dao.insertNodes(incoming.map { it.toEntity() })
            incoming.size
        }
    }
}
