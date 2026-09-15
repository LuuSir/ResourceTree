package com.example.resouretree.data.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Collator

data class InstalledApp(val name: String, val packageName: String, val icon: Bitmap? = null)

fun interface AppCatalog {
    suspend fun load(): List<InstalledApp>
    suspend fun find(packageName: String): InstalledApp? = null
    suspend fun resolveTarget(candidates: List<String>): String =
        candidates.firstOrNull { find(it) != null } ?: candidates.first()
}

class AndroidAppCatalog(context: Context) : AppCatalog {
    private val appContext = context.applicationContext

    override suspend fun resolveTarget(candidates: List<String>): String = withContext(Dispatchers.IO) {
        candidates.firstOrNull { appContext.packageManager.getLaunchIntentForPackage(it) != null } ?: candidates.first()
    }

    @Suppress("DEPRECATION")
    override suspend fun find(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            if (!info.enabled) null else InstalledApp(info.loadLabel(pm).toString(), packageName,
                runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull())
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
    }

    @Suppress("DEPRECATION")
    override suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // The scoped <queries> launcher declaration grants visibility; no runtime storage/network permission.
        val activities = pm.queryIntentActivities(intent, 0)
        val apps = activities.filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
            .distinctBy { it.activityInfo.packageName }.map { resolved ->
                ensureActive()
                val info = resolved.activityInfo.applicationInfo
                InstalledApp(
                    name = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    packageName = info.packageName,
                    icon = runCatching { info.loadIcon(pm).toBitmap(96, 96) }.getOrNull()
                )
            }
        val collator = Collator.getInstance()
        apps.sortedWith { a, b -> collator.compare(a.name, b.name).takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName) }
    }
}
