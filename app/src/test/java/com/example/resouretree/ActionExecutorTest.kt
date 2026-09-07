package com.example.resouretree

import com.example.resouretree.domain.action.*
import com.example.resouretree.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ActionExecutorTest {
    @Test fun copyAlwaysPrecedesLaunch() {
        val calls = mutableListOf<String>()
        val executor = ActionExecutor({ calls += "copy:$it" }, { calls += "launch:$it"; true })
        assertTrue(executor.execute(ResourceAction(ActionType.COPY_AND_LAUNCH, "BV123", "test.app")) is ActionResult.Success)
        assertEquals(listOf("copy:BV123", "launch:test.app"), calls)
    }
    @Test fun missingAppKeepsCopiedTextAndReportsPackage() {
        var copied = ""
        val executor = ActionExecutor({ copied = it }, { false })
        val result = executor.execute(ResourceAction(ActionType.COPY_AND_LAUNCH, "text", "missing.app")) as ActionResult.Error
        assertEquals("text", copied); assertTrue(result.message.contains("已复制")); assertTrue(result.message.contains("missing.app"))
    }
    @Test fun copyDoesNotLaunch() {
        var copied = ""
        ActionExecutor({ copied = it }, { throw AssertionError("Unexpected launch") }).execute(ResourceAction(ActionType.COPY, "abc"))
        assertEquals("abc", copied)
    }
    @Test fun launchDoesNotCopy() {
        var launched = ""
        ActionExecutor({ throw AssertionError("Unexpected copy") }, { launched = it; true }).execute(ResourceAction(ActionType.LAUNCH_APP, packageName = "test.app"))
        assertEquals("test.app", launched)
    }
    @Test fun noneHasNoSideEffects() {
        val result = ActionExecutor({ throw AssertionError() }, { throw AssertionError() }).execute(ResourceAction())
        assertTrue(result is ActionResult.Success)
    }
    @Test fun failedCopyDoesNotLaunch() {
        val result = ActionExecutor({ throw SecurityException("拒绝") }, { throw AssertionError("Must not launch") })
            .execute(ResourceAction(ActionType.COPY_AND_LAUNCH, "abc", "test.app"))
        assertTrue(result is ActionResult.Error)
    }
    @Test fun launchExceptionIsReportedWithoutCrash() {
        val result = ActionExecutor({}, { throw SecurityException("拒绝启动") }).execute(ResourceAction(ActionType.COPY_AND_LAUNCH, "abc", "test.app")) as ActionResult.Error
        assertTrue(result.message.contains("已复制")); assertTrue(result.message.contains("拒绝启动"))
    }
}
