package com.example.resouretree.domain.model

data class ClipboardRule(
    val id: String,
    val prefix: String,
    val name: String = "",
    val targets: List<String>,
    val enabled: Boolean = true,
    val actionType: ActionType = ActionType.COPY_AND_LAUNCH,
    val matchType: ClipboardMatchType = ClipboardMatchType.PREFIX
) {
    fun validated(): ClipboardRule {
        val result = copy(prefix = prefix.trim(), name = name.trim(), targets = targets.map(String::trim).filter(String::isNotEmpty).distinct())
        require(result.prefix.isNotEmpty() && result.prefix.length <= 100) { "请输入 1～100 字的匹配规则" }
        require(result.name.length <= 80) { "默认名称最多 80 字" }
        require(actionType in listOf(ActionType.COPY_AND_LAUNCH, ActionType.OPEN_WEBVIEW)) { "不支持的规则动作" }
        if (actionType == ActionType.OPEN_WEBVIEW) return result.copy(targets = emptyList())
        require(result.targets.size in 1..20) { "请填写 1～20 个候选包名，每行一个" }
        require(result.targets.all { Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it) }) { "包名格式不正确，请每行填写一个完整包名" }
        return result
    }

    fun matches(text: String): Boolean = when (matchType) {
        ClipboardMatchType.PREFIX -> text.startsWith(prefix)
        ClipboardMatchType.WILDCARD -> clipboardGlobMatches(prefix, text)
    }

    val specificity: Int get() = prefix.codePoints().toArray().count {
        matchType == ClipboardMatchType.PREFIX || (it != '*'.code && it != '?'.code)
    }

    companion object {
        val defaults = listOf(
            ClipboardRule("bilibili", "BV", targets = listOf("tv.danmaku.bili", "com.bilibili.app.in")),
            ClipboardRule("taobao", "【淘宝】", "淘宝分享", listOf("com.taobao.taobao"))
        )
    }
}
