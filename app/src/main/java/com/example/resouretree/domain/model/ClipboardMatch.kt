package com.example.resouretree.domain.model

enum class ClipboardMatchType { PREFIX, WILDCARD }

/** Whole-text glob: '*' includes newlines; '?' consumes one Unicode code point. No regex syntax. */
fun clipboardGlobMatches(pattern: String, text: String): Boolean {
    val rule = pattern.codePoints().toArray()
    val input = text.codePoints().toArray()
    var p = 0
    var t = 0
    var star = -1
    var retry = 0
    while (t < input.size) {
        when {
            p < rule.size && rule[p] == '*'.code -> { star = p++; retry = t }
            p < rule.size && (rule[p] == '?'.code || rule[p] == input[t]) -> { p++; t++ }
            star >= 0 -> { p = star + 1; t = ++retry }
            else -> return false
        }
    }
    while (p < rule.size && rule[p] == '*'.code) p++
    return p == rule.size
}
