package com.example.resouretree.domain.model

data class ClipboardDraft(val token: String, val name: String, val text: String, val targets: List<String>) {
    fun target(installedPackages: Set<String>): String = targets.firstOrNull { it in installedPackages } ?: targets.first()
}

object ClipboardDraftParser {
    fun parse(text: String, token: String, rules: List<ClipboardRule> = ClipboardRule.defaults): ClipboardDraft? {
        val prefix = text.trimStart()
        val rule = rules.filter { it.enabled && prefix.startsWith(it.prefix) }.maxByOrNull { it.prefix.length } ?: return null
        return ClipboardDraft(token, rule.name.ifBlank { prefix.lineSequence().first().take(80) }, text, rule.targets)
    }
}
