package com.example.resouretree

import com.example.resouretree.domain.model.ClipboardDraftParser
import org.junit.Assert.*
import org.junit.Test

class ClipboardDraftTest {
    @Test fun bvKeepsFullTextAndUsesInstalledBilibiliEdition() {
        val text = "  BV1meMS6rE6Z\n备注与链接"
        val draft = requireNotNull(ClipboardDraftParser.parse(text, "token"))
        assertEquals(text, draft.text); assertEquals("BV1meMS6rE6Z", draft.name)
        assertEquals("com.bilibili.app.in", draft.target(setOf("com.bilibili.app.in")))
        assertEquals("tv.danmaku.bili", draft.target(setOf("com.bilibili.app.in", "tv.danmaku.bili")))
    }
    @Test fun taobaoKeepsWholeMessageAndHasReadyToSaveName() {
        val text = "【淘宝】优惠商品 https://example.test/a?x=1&y=2\n打开淘宝查看"
        val draft = requireNotNull(ClipboardDraftParser.parse(text, "token"))
        assertEquals(text, draft.text); assertEquals("淘宝分享", draft.name)
        assertEquals("com.taobao.taobao", draft.target(emptySet()))
    }
    @Test fun onlyPrefixesMatch() {
        listOf("", "普通文字", "看看这个BV123", "https://example.test/BV123", "淘宝商品", "bv123").forEach {
            assertNull(ClipboardDraftParser.parse(it, "token"))
        }
    }
}
