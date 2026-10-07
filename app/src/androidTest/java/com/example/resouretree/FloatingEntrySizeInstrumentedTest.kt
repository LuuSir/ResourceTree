package com.example.resouretree

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.overlay.*
import com.example.resouretree.ui.screens.FloatingEntryScreen
import com.example.resouretree.ui.theme.ResoureTreeTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Uses the installed overlay service, while the screen host never reads the user's clipboard. */
@RunWith(AndroidJUnit4::class)
class FloatingEntrySizeInstrumentedTest {
    private val compose = createComposeRule()
    // The timeout encloses ActivityScenario launch and rule cleanup, not just the test body.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(90)).around(compose)

    @Test fun sliderResizesRealOverlayWindowAndKeepsPreferenceAfterRestart() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Existing overlay permission is required", Settings.canDrawOverlays(app))
        val settings = FloatingEntrySettings(app)
        val preferences = settings.preferences
        val hadSize = preferences.contains(FloatingEntrySettings.KEY_SIZE)
        val previousSize = preferences.getInt(FloatingEntrySettings.KEY_SIZE, FloatingEntrySettings.DEFAULT_SIZE)
        val previouslyRunning = FloatingEntryService.running.value
        val service = Intent(app, FloatingEntryService::class.java)
        val dumps = BoundedWindowDump()
        fun start() = compose.runOnIdle { ContextCompat.startForegroundService(app, service) }
        fun awaitControls() = compose.waitUntil(10_000) {
            // External-app tests can leave a pending window transition on the device.
            compose.onAllNodesWithContentDescription("悬浮按钮大小")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        fun awaitWidth(size: Int) {
            val expected = (size * app.resources.displayMetrics.density).roundToInt()
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var observed: List<Pair<Int, Int>> = emptyList()
            while (SystemClock.elapsedRealtime() < deadline) {
                observed = overlayWindowFrames(dumps.read(), app.packageName)
                if (observed.any { it.first == expected && it.second == expected }) return
                SystemClock.sleep(100)
            }
            fail("Overlay did not become ${expected}x$expected px; last observed frames: $observed")
        }
        try {
            app.stopService(service)
            compose.waitUntil(10_000) { !FloatingEntryService.running.value }
            // This isolated screen avoids a clipboard draft changing MainActivity's navigation.
            compose.setContent { ResoureTreeTheme { FloatingEntryScreen(onBack = {}) } }
            start()
            awaitWidth(settings.sizeDp)
            awaitControls()
            compose.onNodeWithContentDescription("悬浮按钮大小").performSemanticsAction(SemanticsActions.SetProgress) { it(84f) }
            awaitWidth(84)
            assertEquals(84, FloatingEntrySettings(app).sizeDp)
            awaitControls()
            compose.onNodeWithText("关闭悬浮按钮", substring = false).performScrollTo().performClick()
            compose.waitUntil(10_000) { !FloatingEntryService.running.value }
            start()
            awaitWidth(84)
            awaitControls()
            compose.onNodeWithText("恢复默认大小").performScrollTo().performClick()
            awaitWidth(FloatingEntrySettings.DEFAULT_SIZE)
        } finally {
            dumps.close()
            app.stopService(service)
            try {
                compose.waitUntil(10_000) { !FloatingEntryService.running.value }
            } finally {
                preferences.edit().apply {
                    if (hadSize) putInt(FloatingEntrySettings.KEY_SIZE, previousSize)
                    else remove(FloatingEntrySettings.KEY_SIZE)
                }.commit()
                if (previouslyRunning) start()
            }
        }
    }

    /** Both shell-side dumpsys and descriptor reads have deadlines; no accessibility window RPCs. */
    private class BoundedWindowDump : Closeable {
        private val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "overlay-window-dump").apply { isDaemon = true }
        }
        private val descriptor = AtomicReference<ParcelFileDescriptor?>()
        private val closed = AtomicBoolean(false)

        fun read(): String {
            val future = executor.submit<String> {
                val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("dumpsys -t 2 window windows")
                descriptor.set(fd)
                if (closed.get()) {
                    descriptor.getAndSet(null)?.close()
                    return@submit ""
                }
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
                } finally {
                    descriptor.compareAndSet(fd, null)
                }
            }
            return try {
                future.get(3, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                close()
                future.cancel(true)
                throw AssertionError("Window dump timed out after 3 seconds", e)
            }
        }

        override fun close() {
            closed.set(true)
            runCatching { descriptor.getAndSet(null)?.close() }
            executor.shutdownNow()
        }
    }
}
