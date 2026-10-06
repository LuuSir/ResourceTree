package com.example.resouretree.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.resouretree.overlay.FloatingEntryService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingEntryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val running by FloatingEntryService.running.collectAsStateWithLifecycle()
    val serviceError by FloatingEntryService.error.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun start() {
        try {
            FloatingEntryService.error.value = null
            ContextCompat.startForegroundService(context, Intent(context, FloatingEntryService::class.java))
        } catch (_: Exception) { error = "暂时无法开启，请保持应用在前台后重试" }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else start()
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Permission settings may return before RESUMED; the lifecycle observer performs the start.
        permitted = Settings.canDrawOverlays(context)
    }
    val onResume by rememberUpdatedState(newValue = {
        permitted = Settings.canDrawOverlays(context)
        if (!permitted && running) context.stopService(Intent(context, FloatingEntryService::class.java))
        if (awaitingPermission) {
            awaitingPermission = false
            if (permitted) enable() else error = "尚未允许显示在其他应用上层，可再次点击开启"
        }
    })
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("悬浮按钮") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("在其他应用复制文字后，点击悬浮的“树”按钮，即可返回首页并按剪贴板规则预填条目。")
            Text("拖动可调整位置。仅回到前台后读取剪贴板，保存前仍需你确认。")
            Text(if (running) "状态：已开启" else "状态：已关闭")
            (error ?: serviceError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                error = null
                if (running) context.stopService(Intent(context, FloatingEntryService::class.java))
                else if (Settings.canDrawOverlays(context)) enable()
                else {
                    awaitingPermission = true
                    try { overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }
                    catch (_: Exception) { awaitingPermission = false; error = "无法打开授权页面，请在系统设置中允许悬浮窗权限" }
                }
            }) { Text(if (running) "关闭悬浮按钮" else "开启悬浮按钮") }
            Text("首次开启需允许“显示在其他应用上层”。运行时的通知也可以返回首页或关闭按钮。部分手机还需允许“后台弹出界面”；若系统停止服务，可回这里重新开启。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
