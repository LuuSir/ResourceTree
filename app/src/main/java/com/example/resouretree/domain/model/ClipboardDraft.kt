package com.example.resouretree.domain.model

data class ClipboardDraft(val token: String, val name: String, val text: String, val targets: List<String>) {
    fun target(installedPackages: Set<String>): String = targets.firstOrNull { it in installedPackages } ?: targets.first()
}

object ClipboardDraftParser {
    fun parse(text: String, token: String): ClipboardDraft? {
        val prefix = text.trimStart()
        return when {
            prefix.startsWith("BV") -> ClipboardDraft(token, prefix.lineSequence().first().take(80), text,
                listOf("tv.danmaku.bili", "com.bilibili.app.in"))
            prefix.startsWith("【淘宝】") -> ClipboardDraft(token, "淘宝分享", text, listOf("com.taobao.taobao"))
            else -> null
        }
    }
}
