package com.example.resouretree

import android.app.NotificationManager
import android.content.Intent
import android.view.MotionEvent
import android.widget.TextView
import com.example.resouretree.overlay.FloatingEntryService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingEntryServiceTest {
    private fun bubble(service: FloatingEntryService): TextView? =
        FloatingEntryService::class.java.getDeclaredField("bubble").apply { isAccessible = true }.get(service) as TextView?

    @Test fun missingOverlayPermissionStopsWithoutWindow() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildService(FloatingEntryService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(), 0, 1)
            assertNull(bubble(service))
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
    }

    @Test fun tapReturnsHomeDragDoesNotLaunchAndStopRemovesWindow() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingEntryService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(), 0, 1)
            assertTrue(FloatingEntryService.error.value, FloatingEntryService.running.value)
            val view = requireNotNull(bubble(service))
            service.onStartCommand(Intent(), 0, 2)
            assertSame(view, bubble(service))
            fun touch(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0, 10, action, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
            touch(MotionEvent.ACTION_DOWN, 20f, 20f)
            touch(MotionEvent.ACTION_MOVE, 150f, 200f)
            touch(MotionEvent.ACTION_UP, 150f, 200f)
            assertNull(shadowOf(service).nextStartedActivity)
            touch(MotionEvent.ACTION_DOWN, 20f, 20f)
            touch(MotionEvent.ACTION_UP, 20f, 20f)
            val home = requireNotNull(shadowOf(service).nextStartedActivity)
            assertEquals(MainActivity::class.java.name, home.component?.className)
            assertEquals(FloatingEntryService.ACTION_HOME, home.action)
            for (flag in listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TOP, Intent.FLAG_ACTIVITY_SINGLE_TOP))
                assertTrue(home.flags and flag != 0)
            val notification = shadowOf(service.getSystemService(NotificationManager::class.java)).getNotification(4201)
            assertNotNull(notification.contentIntent)
            assertEquals("关闭悬浮按钮", notification.actions.single().title)
            service.onStartCommand(Intent().setAction(FloatingEntryService.ACTION_STOP), 0, 3)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
        assertNull(bubble(service))
        assertFalse(FloatingEntryService.running.value)
    }
}
