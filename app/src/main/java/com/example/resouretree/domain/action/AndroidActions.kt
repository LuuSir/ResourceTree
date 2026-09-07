package com.example.resouretree.domain.action

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidClipboardWriter(context: Context) : ClipboardWriter {
    private val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
    override fun write(text: String) { clipboard.setPrimaryClip(ClipData.newPlainText("ResourceTree", text)) }
}

class AndroidPackageLauncher(context: Context) : PackageLauncher {
    private val app = context.applicationContext
    override fun launch(packageName: String): Boolean {
        val intent = app.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        // Android 13+ can resolve the user's package without package visibility access.
        if (Build.VERSION.SDK_INT >= 33) {
            return try {
                app.packageManager.getLaunchIntentSenderForPackage(packageName)
                    .sendIntent(app, 0, null, null, null)
                true
            } catch (_: Exception) { false }
        }
        return false
    }
}
