package com.example.resouretree

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.*
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.action.AndroidContentSharer
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShareInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realImageAndTextReachDifferentUidAndSystemSharesheetOpens() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        compose.setContent { Text("分享验证") }
        val sharer = AndroidContentSharer(context)
        fun receive(content: ResourceContent): Bundle {
            val latch = CountDownLatch(1)
            var received: Bundle? = null
            val callback = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(code: Int, result: Bundle?) { received = result; latch.countDown() }
            }
            val send = sharer.createIntent(content, instrumentation.context.packageName)
                .putExtra("verification", callback)
            compose.runOnIdle { context.startActivity(send) }
            assertTrue("Receiving test app did not respond", latch.await(30, TimeUnit.SECONDS))
            val result = requireNotNull(received)
            assertNull(result.getString("error"))
            assertNotEquals(android.os.Process.myUid(), result.getInt("uid"))
            return result
        }
        val source = File(context.filesDir, "media/verification-${UUID.randomUUID()}.png")
        source.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val expected = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
        var copied: File? = null
        try {
            val pickedUri = FileProvider.getUriForFile(context, "${context.packageName}.media", source)
            val media = runBlocking { DocumentStore(context.contentResolver, source.parentFile).importMedia(pickedUri, ContentType.IMAGE) }
            copied = File(context.filesDir, media.path)
            source.delete() // Imported content must survive removal of the original file.
            val image = receive(media)
            assertEquals("content", image.getString("scheme")); assertEquals("image/png", image.getString("mime"))
            assertEquals(32, image.getInt("width")); assertArrayEquals(expected, image.getByteArray("digest"))
            val text = receive(ResourceContent(text = "ResourceTree 分享文字验证"))
            assertEquals("ResourceTree 分享文字验证", text.getString("text"))
            assertEquals("text/plain", text.getString("mime"))
            compose.runOnIdle { sharer.share(ResourceContent(text = "ResourceTree 系统分享验证"), "") }
            compose.waitUntil(10000) {
                val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val activities = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                activities.lineSequence().any { it.contains("topResumedActivity") && (it.contains("ChooserActivity") || it.contains("ResolverActivity")) }
            }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        } finally { source.delete(); copied?.delete() }
    }
}
