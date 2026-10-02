package com.example.resouretree.ui.web

import android.content.Intent
import android.net.Uri
import com.example.resouretree.domain.model.webUrl
import java.util.Locale

/** Separates an item's web URL from a web page's outbound application link. */
class WebNavigation(
    private val launch: (Intent) -> Unit,
    private val loadFallback: (String) -> Unit,
    private val showError: (String?) -> Unit
) {
    private val opened = mutableSetOf<String>()
    private val fallbacks = mutableSetOf<String>()

    fun reset() { opened.clear(); fallbacks.clear() }

    fun navigate(url: String, mainFrame: Boolean, hasGesture: Boolean): Boolean {
        if (webUrl(url) != null) return false
        // Background frames cannot unexpectedly launch applications; explicit frame clicks may.
        if (!mainFrame && !hasGesture) return true
        if (hasGesture) reset()
        if (url in opened) return true // Some H5 pages retry their automatic jump after returning from the app.
        val link = parseAppLink(url)
        if (link == null) { showError("此链接无法打开，已保留当前网页"); return true }
        try {
            launch(link.intent)
            opened += url
            showError(null)
        } catch (_: android.content.ActivityNotFoundException) {
            fallback(link.fallback)
        } catch (_: SecurityException) {
            fallback(link.fallback)
        }
        return true
    }

    private fun fallback(url: String?) {
        if (url != null && fallbacks.add(url)) {
            showError(null)
            loadFallback(url)
        } else showError("未能打开对应应用，已保留当前网页；请确认应用已安装且允许跳转")
    }
}

internal data class AppLink(val intent: Intent, val fallback: String?)

internal fun parseAppLink(value: String): AppLink? = runCatching {
    val raw = Uri.parse(value)
    val parsed = if (raw.scheme.equals("intent", true)) Intent.parseUri(value, Intent.URI_INTENT_SCHEME) else null
    val data = parsed?.data ?: raw
    val scheme = data.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme in setOf("http", "https")) {
        require(webUrl(data.toString()) != null)
    } else {
        require(Regex("[a-z][a-z0-9+.-]*").matches(scheme))
        require(scheme !in setOf("intent", "android-app", "javascript", "file", "content", "data", "about", "blob"))
    }
    // Rebuild, rather than forwarding web-supplied actions, components, selectors, flags or extras.
    val safe = Intent(Intent.ACTION_VIEW, data).addCategory(Intent.CATEGORY_BROWSABLE)
    parsed?.`package`?.let { target ->
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(target))
        safe.setPackage(target)
    }
    AppLink(safe, parsed?.getStringExtra("browser_fallback_url")?.let(::webUrl))
}.getOrNull()
