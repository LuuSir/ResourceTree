package com.example.resouretree

import android.content.Intent
import android.net.Uri
import com.example.resouretree.domain.action.AndroidContentSharer
import com.example.resouretree.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidShareTest {
    @Test fun textChooserAndDirectedTargetUseCurrentText() {
        val sharer = AndroidContentSharer(RuntimeEnvironment.getApplication())
        val chooser = sharer.createIntent(ResourceContent(text = "分享文本"), "")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action); assertEquals("text/plain", send.type)
        assertEquals("分享文本", send.getStringExtra(Intent.EXTRA_TEXT)); assertNull(send.`package`)
        val directed = sharer.createIntent(ResourceContent(text = "新文本"), "test.app")
        assertEquals(Intent.ACTION_SEND, directed.action); assertEquals("test.app", directed.`package`)
        assertEquals("新文本", directed.getStringExtra(Intent.EXTRA_TEXT))
    }
    @Test fun cannotSharePrivateDatabaseOrMissingMedia() {
        val context = RuntimeEnvironment.getApplication()
        val sharer = AndroidContentSharer(context)
        listOf("media/missing", "media/../secret", "file://${context.getDatabasePath("resource-tree.db")}").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { sharer.createIntent(ResourceContent(ContentType.FILE, path = path, mimeType = "application/octet-stream"), "") }
        }
    }
}
