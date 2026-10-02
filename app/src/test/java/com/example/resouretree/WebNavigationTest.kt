package com.example.resouretree

import android.content.ActivityNotFoundException
import android.content.Intent
import com.example.resouretree.ui.web.WebNavigation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebNavigationTest {
    @Test fun javascriptMainFrameCanOpenDouyinWithoutGestureAndDoesNotRelaunchOnReturn() {
        val intents = mutableListOf<Intent>()
        val handler = WebNavigation({ intents += it }, { fail("fallback") }, { assertNull(it) })
        val url = "snssdk1128://aweme/detail/7685000000000"
        assertTrue(handler.navigate(url, true, false))
        assertEquals(Intent.ACTION_VIEW, intents.single().action)
        assertEquals(url, intents.single().dataString)
        assertTrue(intents.single().hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(handler.navigate(url, true, false)); assertEquals(1, intents.size)
        handler.navigate(url, true, true); assertEquals(2, intents.size)
    }
    @Test fun intentLinksUseDataAndPackageButStripEmbeddedPrivilegesAndExtras() {
        var received: Intent? = null
        val handler = WebNavigation({ received = it }, { fail("fallback") }, { assertNull(it) })
        handler.navigate("intent://aweme/detail/7685#Intent;scheme=snssdk1128;package=com.ss.android.ugc.aweme;action=android.intent.action.DELETE;component=com.ss.android.ugc.aweme/.Private;launchFlags=0x10000003;S.payload=secret;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end", true, false)
        val intent = requireNotNull(received)
        assertEquals("snssdk1128://aweme/detail/7685", intent.dataString)
        assertEquals("com.ss.android.ugc.aweme", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertNull(intent.component); assertNull(intent.selector); assertNull(intent.extras)
        assertEquals(0, intent.flags)
    }
    @Test fun missingAppUsesDecodedWebFallbackOnceWithoutLooping() {
        val fallbacks = mutableListOf<String>()
        var error: String? = null
        val handler = WebNavigation({ throw ActivityNotFoundException() }, { fallbacks += it }, { error = it })
        val url = "intent://aweme/detail/1#Intent;scheme=snssdk1128;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F%3Fid%3D1;end"
        handler.navigate(url, true, false)
        assertEquals(listOf("https://example.com/?id=1"), fallbacks); assertNull(error)
        handler.navigate(url, true, false)
        assertEquals(1, fallbacks.size); assertNotNull(error)
    }
    @Test fun deniedLaunchAndUnsafeFallbackKeepCurrentPage() {
        var error: String? = null
        val handler = WebNavigation({ throw SecurityException() }, { fail("Unsafe fallback") }, { error = it })
        handler.navigate("intent://open/#Intent;scheme=missingapp;S.browser_fallback_url=javascript%3Aalert(1);end", true, false)
        assertNotNull(error)
    }
    @Test fun webRedirectsStayInWebViewAndBackgroundFramesCannotLaunch() {
        var launches = 0
        val handler = WebNavigation({ launches++ }, {}, {})
        assertFalse(handler.navigate("https://www.iesdouyin.com/share/video/7685", true, false))
        assertTrue(handler.navigate("snssdk1128://aweme/detail/7685", false, false))
        assertEquals(0, launches)
        handler.navigate("snssdk1128://aweme/detail/7685", false, true)
        assertEquals(1, launches)
    }
    @Test fun localExecutableAndMalformedSchemesAreNeverDispatched() {
        val handler = WebNavigation({ fail("Unsafe dispatch") }, { fail("fallback") }, {})
        listOf("javascript:alert(1)", "file:///data/private", "content://private/a", "data:text/html,hello", "about:blank", "intent://bad/#Intent;scheme=file;end", "intent://broken/#Intent;scheme=snssdk1128").forEach {
            assertTrue(handler.navigate(it, true, false))
        }
    }
}
