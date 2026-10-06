package com.example.resouretree.overlay

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.resouretree.MainActivity
import com.example.resouretree.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** User-enabled, visible entry point only. Clipboard access belongs exclusively to MainActivity. */
class FloatingEntryService : Service() {
    companion object {
        const val ACTION_HOME = "com.example.resouretree.RETURN_HOME"
        const val ACTION_STOP = "com.example.resouretree.STOP_FLOATING_ENTRY"
        private const val CHANNEL = "floating-entry"
        private const val NOTIFICATION = 4201
        private val state = MutableStateFlow(false)
        val running = state.asStateFlow()
        val error = MutableStateFlow<String?>(null)

        fun homeIntent(context: Context) = Intent(context, MainActivity::class.java).setAction(ACTION_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val prefs by lazy { getSharedPreferences("floating-entry", MODE_PRIVATE) }
    private var bubble: ImageView? = null
    private var layout: WindowManager.LayoutParams? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        if (bubble != null) return START_NOT_STICKY
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "悬浮按钮", NotificationManager.IMPORTANCE_LOW))
            val home = PendingIntent.getActivity(this, 0, homeIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 1, Intent(this, FloatingEntryService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_floating_entry).setContentTitle("ResourceTree 悬浮按钮已开启")
                .setContentText("点击返回首页；也可在此关闭悬浮按钮")
                .setContentIntent(home).addAction(0, "关闭悬浮按钮", stop).setOngoing(true).setOnlyAlertOnce(true).build()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION, notification)
            showBubble()
            error.value = null
            state.value = true
        } catch (_: Exception) {
            error.value = "悬浮按钮启动失败，请检查悬浮窗权限后重试"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showBubble() {
        val params = WindowManager.LayoutParams(dp(56), dp(56), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = prefs.getInt("x", dp(12)); y = prefs.getInt("y", dp(180))
        }
        clamp(params)
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "返回 ResourceTree 首页并读取剪贴板"; isClickable = true
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(255, 241, 219)) }
            elevation = dp(6).toFloat()
            setOnClickListener {
                try { startActivity(homeIntent(this@FloatingEntryService)) }
                catch (_: Exception) { Toast.makeText(this@FloatingEntryService, "无法返回应用，请允许后台弹出界面或从通知打开", Toast.LENGTH_LONG).show() }
            }
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; dragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt(); params.y = startY + dy.roundToInt(); clamp(params)
                        runCatching { windows.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.performClick()
                    else prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { dragging = false; true }
                else -> false
            }
        }
        windows.addView(view, params)
        bubble = view; layout = params
    }

    private fun clamp(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height - dp(48)).coerceAtLeast(0))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubble ?: return
        layout?.let { clamp(it); runCatching { windows.updateViewLayout(view, it) } }
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windows.removeView(it) } }
        bubble = null; layout = null; state.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
