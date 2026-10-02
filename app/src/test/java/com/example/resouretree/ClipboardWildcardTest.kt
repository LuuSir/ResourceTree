package com.example.resouretree

import com.example.resouretree.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ClipboardWildcardTest {
    private fun rule(id: String, pattern: String, type: ClipboardMatchType = ClipboardMatchType.WILDCARD) =
        ClipboardRule(id, pattern, id, listOf("com.test.app"), matchType = type)

    @Test fun starsMatchZeroOrMoreCharactersIncludingNewlinesAndUnicode() {
        listOf("BV*" to "BV", "BV*" to "BV123\n备注", "*链接*" to "分享\n链接😀", "a**b*c" to "abc", "*" to "").forEach {
            assertTrue(it.toString(), clipboardGlobMatches(it.first, it.second))
        }
        assertFalse(clipboardGlobMatches("BV*", "前文BV123"))
        assertFalse(clipboardGlobMatches("*BV", "BV123"))
    }
    @Test fun questionMarkConsumesExactlyOneUnicodeCharacter() {
        assertTrue(clipboardGlobMatches("BV?", "BV1"))
        assertTrue(clipboardGlobMatches("a?b", "a😀b"))
        assertTrue(clipboardGlobMatches("a?b", "a\nb"))
        assertFalse(clipboardGlobMatches("BV?", "BV"))
        assertFalse(clipboardGlobMatches("BV?", "BV12"))
    }
    @Test fun patternsMatchWholeTextAndRegexPunctuationIsLiteral() {
        val pattern = "*https://example.com/a+b[1](x).*"
        assertTrue(clipboardGlobMatches(pattern, "分享 https://example.com/a+b[1](x).尾部"))
        assertFalse(clipboardGlobMatches(pattern, "https://exampleXcom/a+b[1](x)."))
        assertFalse(clipboardGlobMatches("BV", "BV123"))
        assertFalse(clipboardGlobMatches("BV*", "bv123"))
    }
    @Test fun specificRulesWinAndTiesPreservePrefixThenListOrder() {
        val rules = listOf(rule("catchall", "*"), rule("broad", "*BV*"), rule("legacy", "BV", ClipboardMatchType.PREFIX), rule("specific", "BV12*"))
        assertEquals("specific", ClipboardDraftParser.parse("BV123", "t", rules)?.name)
        assertEquals("legacy", ClipboardDraftParser.parse("BV0", "t", rules)?.name)
        val tied = listOf(rule("first", "BV*1"), rule("second", "B*V1"))
        assertEquals("first", ClipboardDraftParser.parse("BV1", "t", tied)?.name)
        assertEquals("second", ClipboardDraftParser.parse("BV1", "t", tied.map { if (it.id == "first") it.copy(enabled = false) else it })?.name)
    }
    @Test fun legacyPrefixKeepsWildcardCharactersLiteralAndOriginalClipboardIsPreserved() {
        val legacy = rule("old", "https://site.test/?id=", ClipboardMatchType.PREFIX)
        assertNull(ClipboardDraftParser.parse("https://site.test/Xid=1", "t", listOf(legacy)))
        assertNotNull(ClipboardDraftParser.parse("https://site.test/?id=1", "t", listOf(legacy)))
        val text = "  分享文案\nhttps://v.douyin.com/example/ 更多文字 "
        val draft = requireNotNull(ClipboardDraftParser.parse(text, "t", listOf(rule("video", "*https://v.douyin.com/*"))))
        assertEquals(text, draft.text)
    }
    @Test fun repeatedWildcardsAndLongNearMissDoNotUseRegexBacktracking() {
        val pattern = "*" + "a".repeat(98) + "b"
        assertFalse(clipboardGlobMatches(pattern, "a".repeat(100000)))
        assertTrue(clipboardGlobMatches("*a*a*a*b", "aaab"))
        assertFalse(clipboardGlobMatches("*a*a*a*b", "aaaa"))
    }
}
