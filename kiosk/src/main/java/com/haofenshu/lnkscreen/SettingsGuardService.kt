package com.haofenshu.lnkscreen

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * 方案 1 (辅助功能拦截)：监控设置应用所有的 Activity，实现白名单过滤。
 * 对除指定的 5 个页面外的所有设置页面进行实时拦截。
 */
class SettingsGuardService : AccessibilityService() {

    // 【白名单】：用户仅允许操作这 5 个页面
    private val allowedActivities = setOf(
        "com.android.settings.Settings\$WifiSettingsActivity",      // WLAN/WiFi
        "com.android.settings.Settings\$DisplaySettingsActivity",   // 显示/亮度
        "com.android.settings.Settings\$DateTimeSettingsActivity",  // 日期/时间
        "com.android.settings.Settings\$ManageApplicationsActivity",// 应用管理
        "com.android.settings.applications.InstalledAppDetails"     // 应用详情
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            // 仅拦截设置应用的相关页面
            if (packageName == "com.android.settings") {
                Log.d("SettingsGuard", "检测到设置应用页面: $className")

                // 如果是大设置应用内的类，且不在白名单中
                // 特意排除掉：Dialog、Toast 等非全屏组件（通常类名不以 com.android.settings 开头）
                if (className.startsWith("com.android.settings") && !allowedActivities.contains(className)) {
                    Log.w("SettingsGuard", "非法访问页面 [$className]，正在拦截并强制返回...")
                    
                    // 执行返回操作（模拟按下物理返回键）
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w("SettingsGuard", "辅助拦截服务被中断")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("SettingsGuard", "设置应用安全拦截拦截服务已连接")
    }
}
