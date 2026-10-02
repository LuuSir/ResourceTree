package com.example.resouretree.domain.model

data class ClipboardDraft(val token: String, val name: String, val text: String, val targets: List<String>, val actionType: ActionType = ActionType.COPY_AND_LAUNCH) {
    fun target(installedPackages: Set<String>): String = if (actionType == ActionType.OPEN_WEBVIEW) "" else targets.firstOrNull { it in installedPackages } ?: targets.first()
}

object ClipboardDraftParser {
    fun parse(text: String, token: String, rules: List<ClipboardRule> = ClipboardRule.defaults): ClipboardDraft? {
        val prefix = text.trimStart()
        val rule = rules.filter { it.enabled && it.matches(prefix) }.maxWithOrNull(
            compareBy<ClipboardRule> { it.specificity }.thenBy { it.matchType == ClipboardMatchType.PREFIX }
        ) ?: return null
        return ClipboardDraft(token, rule.name.ifBlank { prefix.lineSequence().first().take(80) }, text,
            if (rule.actionType == ActionType.OPEN_WEBVIEW) emptyList() else rule.targets, rule.actionType)
    }
}
