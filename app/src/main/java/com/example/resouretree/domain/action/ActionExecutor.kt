package com.example.resouretree.domain.action

import com.example.resouretree.domain.model.*

fun interface ClipboardWriter { fun write(text: String) }
fun interface PackageLauncher { fun launch(packageName: String): Boolean }
fun interface ContentSharer { fun share(content: ResourceContent, target: String) }

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Error(val message: String) : ActionResult
}

class ActionExecutor(private val clipboard: ClipboardWriter, private val launcher: PackageLauncher,
    private val sharer: ContentSharer = ContentSharer { _, _ -> error("分享服务不可用") }) {
    fun execute(content: ResourceContent, action: ResourceAction): ActionResult {
        var copied = false
        return try {
            if (action.type == ActionType.COPY || action.type == ActionType.COPY_AND_LAUNCH) {
                require(content.type == ContentType.TEXT) { "此内容不能复制为文字，请使用分享" }
                clipboard.write(content.text)
                copied = true
            }
            if (action.type == ActionType.LAUNCH_APP || action.type == ActionType.COPY_AND_LAUNCH) {
                if (action.target.isBlank() || !launcher.launch(action.target)) {
                    return ActionResult.Error("${if (copied) "已复制；" else ""}未找到目标应用\n${action.target}")
                }
            }
            if (action.type == ActionType.SHARE) sharer.share(content, action.target)
            ActionResult.Success(when {
                copied -> "已复制"
                action.type == ActionType.NONE -> "此条目未设置动作"
                else -> "已打开应用"
            })
        } catch (e: Exception) {
            ActionResult.Error("${if (copied) "已复制；" else ""}动作执行失败：${e.message ?: "系统拒绝操作"}")
        }
    }
}
