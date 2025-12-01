package com.yunxiao.appmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.yunxiao.appmanager.R
import com.yunxiao.appmanager.floatwindow.MiniFloatWindow
import com.yunxiao.appmanager.receiver.AppInstallReceiver
import com.yunxiao.appmanager.utils.PermissionHelper

/**
 * 静默监控服务
 * 维持最小悬浮窗，监听应用安装事件，自动启动新安装的应用
 */
class SilentMonitorService : Service() {

    companion object {
        private const val TAG = "SilentMonitorService"
        private const val NOTIFICATION_CHANNEL_ID = "silent_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.yunxiao.appmanager.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.yunxiao.appmanager.STOP_SERVICE"
    }

    private var miniFloatWindow: MiniFloatWindow? = null
    private var appInstallReceiver: AppInstallReceiver? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "监控服务创建")

        initNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "监控服务启动命令")

        // 对于 Android 8.0+，必须立即调用 startForeground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startMonitoring()
            }
            ACTION_STOP_SERVICE -> {
                stopMonitoring()
                stopSelf()
            }
            else -> {
                startMonitoring()
            }
        }

        return START_STICKY  // 服务被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "监控服务销毁")
        stopMonitoring()
        super.onDestroy()
    }

    /**
     * 开始监控
     */
    private fun startMonitoring() {
        try {
            // 1. 检查并请求悬浮窗权限
            if (!PermissionHelper.hasOverlayPermission(this)) {
                Log.w(TAG, "缺少悬浮窗权限")
                // 对于 Android 8.0+，即使权限不足也要保持前台服务运行
                // Android 8.0 以下直接停止服务
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    stopSelf()
                }
                return
            }

            // 2. 创建并显示最小悬浮窗
            createMiniFloatWindow()

            // 3. 注册应用安装监听
            registerAppInstallReceiver()

            Log.d(TAG, "监控服务启动成功")

        } catch (e: Exception) {
            Log.e(TAG, "启动监控服务失败", e)
            // 前台服务已经启动，不能直接停止，但可以记录错误
        }
    }

    /**
     * 停止监控
     */
    private fun stopMonitoring() {
        try {
            // 隐藏悬浮窗
            miniFloatWindow?.release()
            miniFloatWindow = null

            // 注销广播接收器
            appInstallReceiver?.let { receiver ->
                unregisterReceiver(receiver)
            }
            appInstallReceiver = null

            Log.d(TAG, "监控服务停止")

        } catch (e: Exception) {
            Log.e(TAG, "停止监控服务失败", e)
        }
    }

    /**
     * 创建最小悬浮窗
     */
    private fun createMiniFloatWindow() {
        miniFloatWindow = MiniFloatWindow(this)
        miniFloatWindow?.show()
        Log.d(TAG, "最小悬浮窗已创建")
    }

    /**
     * 注册应用安装监听广播
     */
    private fun registerAppInstallReceiver() {
        if (appInstallReceiver == null) {
            appInstallReceiver = AppInstallReceiver()
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        registerReceiver(appInstallReceiver, filter)
        Log.d(TAG, "应用安装监听已注册")
    }

    /**
     * 初始化通知渠道
     */
    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "应用管理监控服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听应用安装并自动启动新应用"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    @Suppress("DEPRECATION")
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用通知渠道和静音设置
            val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("应用管理监控")
                .setContentText("正在监听应用安装")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)

            // 使用反射调用 setSilent，避免编译错误
            try {
                val setSilentMethod = Notification.Builder::class.java.getMethod("setSilent", Boolean::class.java)
                setSilentMethod.invoke(builder, true)
            } catch (e: Exception) {
                // 反射失败时忽略静音设置
                Log.w(TAG, "无法设置通知为静音模式", e)
            }

            builder.build()
        } else {
            // Android 8.0 以下版本使用传统方式
            Notification.Builder(this)
                .setContentTitle("应用管理监控")
                .setContentText("正在监听应用安装")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_LOW)
                .setSound(null) // 低版本设置静音的方式
                .setVibrate(longArrayOf(0L)) // 设置无震动
                .build()
        }
    }

    /**
     * 获取服务状态
     */
    fun isServiceRunning(): Boolean {
        return miniFloatWindow != null && miniFloatWindow!!.isShowing()
    }

    /**
     * 更新通知内容
     */
    @Suppress("DEPRECATION")
    private fun updateNotification(text: String) {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0+ 使用通知渠道和静音设置
            val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("应用管理监控")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)

            // 使用反射调用 setSilent，避免编译错误
            try {
                val setSilentMethod = Notification.Builder::class.java.getMethod("setSilent", Boolean::class.java)
                setSilentMethod.invoke(builder, true)
            } catch (e: Exception) {
                // 反射失败时忽略静音设置
                Log.w(TAG, "无法设置通知为静音模式", e)
            }

            builder.build()
        } else {
            // Android 8.0 以下版本使用传统方式
            Notification.Builder(this)
                .setContentTitle("应用管理监控")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(Notification.PRIORITY_LOW)
                .setSound(null) // 低版本设置静音的方式
                .setVibrate(longArrayOf(0L)) // 设置无震动
                .build()
        }

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
}