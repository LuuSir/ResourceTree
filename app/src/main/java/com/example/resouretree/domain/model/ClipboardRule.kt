package com.example.resouretree.domain.model

data class ClipboardRule(
    val id: String,
    val prefix: String,
    val name: String = "",
    val targets: List<String>,
    val enabled: Boolean = true
) {
    fun validated(): ClipboardRule {
        val result = copy(prefix = prefix.trim(), name = name.trim(), targets = targets.map(String::trim).filter(String::isNotEmpty).distinct())
        require(result.prefix.isNotEmpty() && result.prefix.length <= 100) { "请输入 1～100 字的匹配前缀" }
        require(result.name.length <= 80) { "默认名称最多 80 字" }
        require(result.targets.size in 1..20) { "请填写 1～20 个候选包名，每行一个" }
        require(result.targets.all { Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it) }) { "包名格式不正确，请每行填写一个完整包名" }
        return result
    }

    companion object {
        val defaults = listOf(
            ClipboardRule("bilibili", "BV", targets = listOf("tv.danmaku.bili", "com.bilibili.app.in")),
            ClipboardRule("taobao", "【淘宝】", "淘宝分享", listOf("com.taobao.taobao"))
        )
    }
}
