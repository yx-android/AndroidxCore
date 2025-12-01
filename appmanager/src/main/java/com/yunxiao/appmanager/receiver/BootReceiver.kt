package com.yunxiao.appmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机启动接收器
 * 系统启动后自动启动监控服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "系统启动完成，启动监控服务")
                startMonitorService(context)
            }

            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // 应用自身更新后重新启动服务
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.d(TAG, "应用更新完成，重启监控服务")
                    startMonitorService(context)
                }
            }
        }
    }

    /**
     * 启动监控服务
     */
    private fun startMonitorService(context: Context) {
        try {
            val serviceIntent = Intent(context, com.yunxiao.appmanager.service.SilentMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "监控服务启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动监控服务失败", e)
        }
    }
}