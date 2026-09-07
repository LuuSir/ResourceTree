package com.example.resouretree.domain.action

import com.example.resouretree.domain.model.ActionType
import com.example.resouretree.domain.model.ResourceAction

fun interface ClipboardWriter { fun write(text: String) }
fun interface PackageLauncher { fun launch(packageName: String): Boolean }

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Error(val message: String) : ActionResult
}

class ActionExecutor(private val clipboard: ClipboardWriter, private val launcher: PackageLauncher) {
    fun execute(action: ResourceAction): ActionResult {
        var copied = false
        return try {
            if (action.type == ActionType.COPY || action.type == ActionType.COPY_AND_LAUNCH) {
                clipboard.write(action.text)
                copied = true
            }
            if (action.type == ActionType.LAUNCH_APP || action.type == ActionType.COPY_AND_LAUNCH) {
                if (action.packageName.isBlank() || !launcher.launch(action.packageName)) {
                    return ActionResult.Error("${if (copied) "已复制；" else ""}未找到目标应用\n${action.packageName}")
                }
            }
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
