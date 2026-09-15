package com.example.resouretree

import com.example.resouretree.data.local.entity.*
import com.example.resouretree.data.transfer.TreeJson
import com.example.resouretree.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TreeAndJsonTest {
    private val codec = TreeJson()
    private fun node(parent: String? = null, type: NodeType = NodeType.FOLDER, name: String = "目录") =
        ResourceNode(UUID.randomUUID().toString(), parent, type, name, createdAt = 100, updatedAt = 200)

    @Test fun entityDomainDtoJsonRoundTripPreservesAllFields() {
        val root = node(name = "哔哩哔哩").copy(isPinned = true)
        val folder = node(root.id, name = "生产力")
        val empty = node(root.id, name = "空目录").copy(sortOrder = 1)
        val item = node(folder.id, NodeType.ITEM, "三国\n\"锐评\"").copy(content = ResourceContent(text = "BV123"),
            tags = listOf("三国", "历史", "emoji 🌲", "quote\""),
            action = ResourceAction(ActionType.COPY_AND_LAUNCH, "tv.danmaku.bili"))
        val source = listOf(root, folder, empty, item)
        val fromStorage = source.map { it.toEntity().toDomain() }
        val dto = codec.toDto(fromStorage)
        val decoded = codec.decode(codec.encode(dto))
        assertEquals(dto, decoded)
        assertEquals(source.associateBy { it.id }, codec.toNodes(decoded).associateBy { it.id })
        assertTrue(decoded.roots.single().children.last().children.isEmpty())
    }
    @Test fun breadcrumbTracksParents() {
        val root = node(); val child = node(root.id); val leaf = node(child.id, NodeType.ITEM)
        assertEquals(listOf(root, child, leaf), Tree.breadcrumb(listOf(leaf, root, child), leaf.id))
        assertEquals(emptyList<ResourceNode>(), Tree.breadcrumb(listOf(root), null))
    }
    @Test fun descendantsIncludeOnlySelectedSubtree() {
        val root = node(); val child = node(root.id); val leaf = node(child.id, NodeType.ITEM); val sibling = node()
        assertEquals(setOf(root.id, child.id, leaf.id), Tree.descendants(listOf(root, child, sibling, leaf), root.id))
    }
    @Test fun orphanIsRejected() { rejects { Tree.validate(listOf(node(UUID.randomUUID().toString()))) } }
    @Test fun cyclesAreRejected() {
        val a = node(); val b = node(a.id)
        rejects { Tree.validate(listOf(a.copy(parentId = b.id), b)) }
    }
    @Test fun itemCannotBeParent() { val item = node(type = NodeType.ITEM); rejects { Tree.validate(listOf(item, node(item.id))) } }
    @Test fun sortOrderControlsExportOrder() {
        val first = node(name = "第一").copy(sortOrder = 0)
        val second = node(name = "第二").copy(sortOrder = 2)
        assertEquals(listOf("第一", "第二"), codec.toDto(listOf(second, first)).roots.map { it.name })
    }
    @Test fun duplicateAndExistingIdsAreRemappedWithCorrectParents() {
        val duplicate = UUID.randomUUID().toString()
        val file = codec.decode("""{"schemaVersion":1,"roots":[{"id":"$duplicate","type":"folder","name":"一","children":[{"id":"$duplicate","type":"item","name":"子","tags":[]}]},{"id":"$duplicate","type":"folder","name":"二"}]}""")
        val nodes = codec.toNodes(file, setOf(duplicate))
        assertEquals(3, nodes.map { it.id }.toSet().size)
        assertFalse(nodes.any { it.id == duplicate })
        assertEquals(nodes[0].id, nodes[1].parentId)
        assertNull(nodes[2].parentId)
    }
    @Test fun legacyIdsAndMissingIdsGetUuids() {
        val nodes = codec.toNodes(codec.decode("""{"schemaVersion":1,"roots":[{"id":"bilibili","type":"folder","name":"哔哩哔哩","children":[{"type":"item","name":"测试"}]}]}"""))
        nodes.forEach { assertEquals(it.id, UUID.fromString(it.id).toString()) }
        assertEquals(nodes[0].id, nodes[1].parentId)
    }
    @Test fun missingActionAndEmptyTagsAreSafe() {
        val item = codec.toNodes(codec.decode("""{"schemaVersion":1,"roots":[{"type":"item","name":"无动作","tags":[]}]}""")).single()
        assertEquals(ResourceAction(), item.action); assertEquals(emptyList<String>(), item.tags)
    }
    @Test fun v1MissingActionTextUsesContent() {
        val item = codec.toNodes(codec.decode("""{"schemaVersion":1,"roots":[{"type":"item","name":"复制","content":"文字","action":{"type":"COPY"}}]}""")).single()
        assertEquals("文字", item.content.text)
    }
    @Test fun v1EmptyActionTextDoesNotOverrideContent() {
        val item = codec.toNodes(codec.decode("""{"schemaVersion":1,"roots":[{"type":"item","name":"复制","content":"文字","action":{"type":"COPY","text":""}}]}""")).single()
        assertEquals("文字", item.content.text)
    }
    @Test fun illegalJsonIsRejected() { rejects { codec.decode("{broken") } }
    @Test fun missingRequiredFieldsAreRejected() {
        listOf("{}", """{"schemaVersion":1}""", """{"schemaVersion":1,"roots":[{"type":"item"}]}""").forEach { text -> rejects { codec.decode(text) } }
    }
    @Test fun unsupportedVersionHasClearError() {
        assertTrue(rejects { codec.decode("""{"schemaVersion":3,"roots":[]}""") }.message.orEmpty().contains("更新版本"))
        rejects { codec.decode("""{"schemaVersion":0,"roots":[]}""") }
    }
    @Test fun invalidTypesAreRejected() {
        listOf("""{"type":"video","name":"x"}""", """{"type":"item","name":"x","tags":[2]}""",
            """{"type":"item","name":"x","sortOrder":1.5}""", """{"type":"item","name":"x","action":{"type":"FUTURE"}}""",
            """{"type":"item","name":"x","action":{"type":"LAUNCH_APP"}}""",
            """{"type":"item","name":"x","children":[{"type":"folder","name":"y"}]}""")
            .forEach { rejects { codec.decode("""{"schemaVersion":1,"roots":[$it]}""") } }
    }
    @Test fun excessiveDepthAndSizeAreRejectedWithoutStackOverflow() {
        rejects { codec.decode("[".repeat(500)) }
        rejects { codec.decode(" ".repeat(TreeJson.MAX_BYTES + 1)) }
    }
    @Test fun emptyFileRootsRoundTrip() { val dto = codec.decode("""{"schemaVersion":1,"roots":[]}"""); assertEquals(dto, codec.decode(codec.encode(dto))) }
    @Test fun bomIsAccepted() { assertTrue(codec.decode("\uFEFF{\"schemaVersion\":1,\"roots\":[]}").roots.isEmpty()) }
    @Test fun tagsInputNormalizesCommasAndDuplicates() { assertEquals(listOf("三国", "历史"), TagsCodec.parseInput(" 三国,历史，三国,, ")) }
    @Test fun deliveredExampleIsImportableWithRequiredStructure() {
        val file = java.io.File("../examples/resource-tree-v1.json")
        val dto = codec.decode(file.readText(Charsets.UTF_8))
        assertEquals("哔哩哔哩", dto.roots.single().name)
        assertEquals(listOf("生产力", "生命力"), dto.roots.single().children.map { it.name })
        val nodes = codec.toNodes(dto)
        assertEquals(3, nodes.count { it.type == NodeType.FOLDER })
        assertEquals(3, nodes.count { it.type == NodeType.ITEM })
        assertEquals(dto, codec.decode(codec.encode(dto)))
    }

    private fun rejects(block: () -> Unit): IllegalArgumentException {
        try { block() } catch (e: IllegalArgumentException) { return e }
        throw AssertionError("Expected validation failure")
    }
}
