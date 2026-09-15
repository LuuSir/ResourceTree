package com.example.resouretree

import com.example.resouretree.data.transfer.TreeJson
import com.example.resouretree.data.local.entity.*
import com.example.resouretree.domain.action.*
import com.example.resouretree.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ContentV2Test {
    private val codec = TreeJson()
    @Test fun v1UsesContentAndOnlyFallsBackWhenEmpty() {
        fun read(content: String, oldText: String) = codec.toNodes(codec.decode("""{"schemaVersion":1,"roots":[
            {"type":"item","name":"旧条目","content":"$content","action":{"type":"COPY_AND_LAUNCH","text":"$oldText","packageName":"com.bilibili.app.in"}}]}""")).single()
        assertEquals("原内容", read("原内容", "不同的旧副本").content.text)
        assertEquals("兼容文字", read("", "兼容文字").content.text)
        assertEquals("com.bilibili.app.in", read("原内容", "").action.target)
    }
    @Test fun v2BilibiliRoundTripHasNoLegacyActionFields() {
        val dto = codec.decode("""{"schemaVersion":2,"roots":[{"type":"item","name":"B站",
            "content":{"type":"TEXT","text":"BV1meMS6rE6Z"},"action":{"type":"COPY_AND_LAUNCH","target":"com.bilibili.app.in"}}]}""")
        val node = codec.toNodes(dto).single()
        val calls = mutableListOf<String>()
        ActionExecutor({ calls.add(it) }, { calls.add(it); true }).execute(node.content, node.action)
        assertEquals(listOf("BV1meMS6rE6Z", "com.bilibili.app.in"), calls)
        val encoded = codec.encode(dto)
        assertFalse(encoded.contains("packageName"))
        val action = kotlinx.serialization.json.Json.parseToJsonElement(encoded)
            .let { it as kotlinx.serialization.json.JsonObject }["roots"]
            .let { it as kotlinx.serialization.json.JsonArray }[0]
            .let { it as kotlinx.serialization.json.JsonObject }["action"] as kotlinx.serialization.json.JsonObject
        assertEquals(setOf("type", "target"), action.keys)
        assertEquals(dto, codec.decode(encoded))
    }
    @Test fun allMediaTypesRoundTripThroughEntityAndJson() {
        val nodes = listOf(ContentType.IMAGE to "image/webp", ContentType.VIDEO to "video/mp4", ContentType.FILE to "application/pdf").map { (type, mime) ->
            ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, type.name,
                content = ResourceContent(type, path = "media/example", mimeType = mime), action = ResourceAction(ActionType.SHARE))
        }
        assertEquals(nodes, nodes.map { it.toEntity().toDomain() })
        assertEquals(nodes.toSet(), codec.toNodes(codec.decode(codec.encode(codec.toDto(nodes)))).toSet())
    }
    @Test fun shareReceivesCurrentContentAndOptionalTarget() {
        val received = mutableListOf<Pair<ResourceContent, String>>()
        val executor = ActionExecutor({ error("copy") }, { error("launch") }, { c, t -> received.add(c to t) })
        val content = ResourceContent(ContentType.IMAGE, path = "media/picture", mimeType = "image/png")
        assertTrue(executor.execute(content, ResourceAction(ActionType.SHARE)) is ActionResult.Success)
        executor.execute(content.copy(path = "media/new"), ResourceAction(ActionType.SHARE, "test.app"))
        assertEquals(listOf(content to "", content.copy(path = "media/new") to "test.app"), received)
    }
    @Test fun mediaCannotAccidentallyCopyEmptyTextOrLaunch() {
        val result = ActionExecutor({ error("copy") }, { error("launch") }).execute(
            ResourceContent(ContentType.IMAGE, path = "media/x", mimeType = "image/png"), ResourceAction(ActionType.COPY_AND_LAUNCH, "test.app"))
        assertTrue(result is ActionResult.Error)
    }
    @Test fun malformedV2MediaAndUnknownTypesAreRejected() {
        listOf("""{"type":"IMAGE","path":"","mimeType":"image/png"}""",
            """{"type":"VIDEO","path":"media/x","mimeType":"image/png"}""",
            """{"type":"FUTURE"}""", """"old string"""").forEach { content ->
            assertThrows(IllegalArgumentException::class.java) { codec.decode("""{"schemaVersion":2,"roots":[{"type":"item","name":"x","content":$content}]}""") }
        }
    }
}
