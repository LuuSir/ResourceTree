package com.example.resouretree

import android.content.Intent
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DouyinWebInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actualShortLinkRedirectsThroughH5AndOpensInstalledDouyin() {
        compose.setContent { Text("抖音网页跳转验证") }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageManager.getLaunchIntentForPackage("com.ss.android.ugc.aweme") != null)
        ActivityScenario.launch<WebPageActivity>(Intent(context, WebPageActivity::class.java)
            .putExtra("url", "https://v.douyin.com/bdRNyijQu_s/")).use {
            compose.waitUntil(45000) {
                SystemClock.sleep(250)
                val fd = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val state = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                state.lineSequence().any { line -> line.contains("topResumedActivity") && line.contains("com.ss.android.ugc.aweme/") }
            }
        }
    }
}
