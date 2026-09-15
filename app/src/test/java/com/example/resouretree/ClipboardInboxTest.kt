package com.example.resouretree

import android.content.ClipData
import android.net.Uri
import com.example.resouretree.data.clipboard.ClipboardDraftInbox
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardInboxTest {
    @Test fun handledClipboardIsNotOfferedAgainEvenAfterInboxRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val inbox = ClipboardDraftInbox(context)
        val clip = ClipData.newPlainText("外部应用", "BV123")
        inbox.offer(clip)
        inbox.consumed(requireNotNull(inbox.pending.value).token)
        inbox.offer(clip); assertNull(inbox.pending.value)
        val recreated = ClipboardDraftInbox(context)
        recreated.offer(clip); assertNull(recreated.pending.value)
        recreated.offer(ClipData.newPlainText("外部应用", "【淘宝】商品"))
        assertNotNull(recreated.pending.value)
    }
    @Test fun ownCopyNonmatchingOrUriClipboardCannotCreateStaleDrafts() {
        val inbox = ClipboardDraftInbox(RuntimeEnvironment.getApplication())
        val clips = listOf(ClipData.newPlainText("ResourceTree", "BV123"),
            ClipData.newPlainText("外部应用", "普通文字"), ClipData.newRawUri("文件", Uri.parse("content://test/picture")), null)
        clips.forEach {
            inbox.offer(ClipData.newPlainText("外部应用", "BV123")); assertNotNull(inbox.pending.value)
            inbox.offer(it); assertNull(inbox.pending.value)
        }
    }
}
