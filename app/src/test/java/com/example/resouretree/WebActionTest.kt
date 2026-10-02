package com.example.resouretree

import com.example.resouretree.domain.action.*
import com.example.resouretree.domain.model.*
import com.example.resouretree.data.local.entity.*
import com.example.resouretree.data.transfer.TreeJson
import org.junit.Assert.*
import org.junit.Test

class WebActionTest {
    @Test fun opensContentWithoutCopyOrTargetLaunch() {
        var opened = ""
        val executor = ActionExecutor({ throw AssertionError("copy") }, { throw AssertionError("launch") }, web = { opened = it })
        assertTrue(executor.execute(ResourceContent(text = " https://example.com/a?q=中文#b "), ResourceAction(ActionType.OPEN_WEBVIEW, "ignored.app")) is ActionResult.Success)
        assertEquals("https://example.com/a?q=中文#b", opened)
    }
    @Test fun invalidExecutableAndLocalAddressesAreRejected() {
        val executor = ActionExecutor({}, { true }, web = { throw AssertionError("Must not open") })
        listOf("", "example.com", "javascript:alert(1)", "file:///data/private", "content://example/x", "intent://example", "https://", "https://user:secret@example.com", "https://example.com/ text", "https://example.com\nhttps://other.test").forEach {
            assertNull(it, webUrl(it))
            assertTrue(executor.execute(ResourceContent(text = it), ResourceAction(ActionType.OPEN_WEBVIEW)) is ActionResult.Error)
        }
        assertTrue(executor.execute(ResourceContent(ContentType.IMAGE, text = "https://example.com"), ResourceAction(ActionType.OPEN_WEBVIEW)) is ActionResult.Error)
    }
    @Test fun webActionRoundTripsEntityAndV2WithoutTarget() {
        val node = ResourceNode(java.util.UUID.randomUUID().toString(), null, NodeType.ITEM, "网页", content = ResourceContent(text = "https://example.com"), action = ResourceAction(ActionType.OPEN_WEBVIEW))
        assertEquals(node, node.toEntity().toDomain())
        val codec = TreeJson()
        assertEquals(node, codec.toNodes(codec.decode(codec.encode(codec.toDto(listOf(node))))).single())
        val imported = codec.toNodes(codec.decode("""{"schemaVersion":2,"roots":[{"type":"item","name":"web","content":{"type":"TEXT","text":"https://example.com"},"action":{"type":"OPEN_WEBVIEW","target":"stale.app"}}]}""")).single()
        assertEquals("", imported.action.target)
    }
    @Test fun webRuleRequiresNoPackagesAndDraftCarriesAction() {
        val rule = ClipboardRule("web", "https://", targets = listOf("stale.app"), actionType = ActionType.OPEN_WEBVIEW).validated()
        assertTrue(rule.targets.isEmpty())
        val draft = requireNotNull(ClipboardDraftParser.parse("https://example.com", "test", listOf(rule)))
        assertEquals(ActionType.OPEN_WEBVIEW, draft.actionType)
        assertEquals("", draft.target(emptySet()))
        assertTrue(draft.targets.isEmpty())
    }
}
