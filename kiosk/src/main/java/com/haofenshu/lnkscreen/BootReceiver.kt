package com.haofenshu.lnkscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 开机自启动接收器
 * 确保应用在设备重启后自动启动并恢复Kiosk模式
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val LAUNCH_DELAY_MS = 3000L // 延迟3秒启动，确保系统就绪
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "收到开机完成广播，准备启动应用")

        // 使用Handler延迟启动，确保系统服务就绪
        Handler(Looper.getMainLooper()).postDelayed({
            launchApp(context)
        }, LAUNCH_DELAY_MS)
    }

    private fun launchApp(context: Context) {
        try {
            // 首先尝试重新初始化Kiosk模式（如果有权限）
            initKioskModeIfPossible(context)

            // 使用通用启动管理器启动应用
            val success = KioskLaunchManager.launchApp(context)

            if (success) {
                Log.d(TAG, "应用启动成功")
            } else {
                Log.e(TAG, "应用启动失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动应用异常", e)
        }
    }

    /**
     * 尝试初始化Kiosk模式
     * 开机后需要重新设置，确保应用保持在单应用模式
     */
    private fun initKioskModeIfPossible(context: Context) {
        try {
            // 检查是否为设备所有者
            if (KioskUtils.isDeviceOwner(context)) {

                // 设置增强的Kiosk模式（包含白名单、状态栏保留、屏蔽恢复出厂设置等）
                val setupSuccess: Boolean =
                    KioskUtils.setupEnhancedKioskMode(context.applicationContext)

                if (setupSuccess) {
                    // 确保当前应用可以被更新
                    KioskUtils.enableAppUpdate(context.applicationContext, null)
                    Log.d("App", "增强Kiosk模式设置成功")
                } else {
                    Log.w("App", "增强Kiosk模式设置失败")
                }

                Log.d(TAG, "Kiosk模式初始化完成")
            } else {
                Log.w(TAG, "非设备所有者，跳过Kiosk模式初始化")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kiosk模式初始化失败", e)
        }
    }
}