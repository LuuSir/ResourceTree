package com.example.resouretree

import android.os.Bundle
import android.content.ClipboardManager
import androidx.lifecycle.Lifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.resouretree.ui.navigation.ResourceTreeApp
import com.example.resouretree.ui.theme.ResoureTreeTheme

class MainActivity : ComponentActivity() {
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { checkClipboard() }

    override fun onStart() {
        super.onStart()
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }
    override fun onStop() {
        clipboard.removePrimaryClipChangedListener(clipboardListener)
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        window.decorView.post { checkClipboard() }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }
    private fun checkClipboard() {
        if (!hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        // A manufacturer may deny clipboard access; that must not interrupt browsing or editing.
        val clip = try { clipboard.primaryClip } catch (_: SecurityException) { return }
        (application as ResourceTreeApplication).clipboardDrafts.offer(clip)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResoureTreeTheme {
                ResourceTreeApp(application as ResourceTreeApplication)
            }
        }
    }
}
