package com.haofenshu.lnkscreen

import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.DEVICE_POLICY_SERVICE
import android.util.Log


fun Application.initKioskMode() {
    try {
        Log.d("App", "开始初始化Kiosk模式")

        // 设置增强的Kiosk模式（包含白名单、状态栏保留、屏蔽恢复出厂设置等）
        val setupSuccess: Boolean = KioskUtils.setupEnhancedKioskMode(this)

        if (setupSuccess) {
            // 确保当前应用可以被更新
            KioskUtils.enableAppUpdate(this, null)
            Log.d("App", "增强Kiosk模式设置成功")
        } else {
            Log.w("App", "增强Kiosk模式设置失败")
        }
    } catch (e: Exception) {
        Log.e("App", "Kiosk模式初始化失败", e)
    }
}

fun Application.addAppsToWhitelist(packageNames: Collection<String>) {
    KioskUtils.addAppsToWhitelist(this, packageNames)
}

fun Application.removeAppsFromWhitelist(packageNames: Collection<String>) {
    KioskUtils.removeAppsFromWhitelist(this, packageNames)
}

/**
 * 启动锁定任务模式（在Activity中调用）
 * 这个方法供Activity调用来实际启动LockTask。
 *
 * 【完美 Kiosk 模式（单应用锁定）的三要素】
 * 根据 Google 官方文档和企业级 MDM 方案，要实现完美的单应用锁定（防止应用切后台或被杀死），必须同时满足以下三点：
 * 1. Device Owner 白名单：通过 DevicePolicyManager.setLockTaskPackages() 将应用包名设为白名单（当前代码中已包含）。
 * 2. 清单文件声明：在 AndroidManifest.xml 的对应 Activity 中配置 `android:lockTaskMode="if_whitelisted"`。
 * 3. 代码启动：在 Activity 处于前台时（如 onResume 中），主动调用 `Activity.startLockTask()`。
 * 缺一不可。
 */
fun Application.startLockTaskIfNeeded(context: Context, applicationId: String) {
    try {
        val dpm = context.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin: ComponentName = ComponentName(context, MyDeviceAdminReceiver::class.java)
        val newPackages = KioskWhitelistManager.getInstance(context).getWhitelistAppsForKiosk()
        // 设置允许在LockTask模式中运行的应用白名单（仅当前应用）
        dpm.setLockTaskPackages(admin, newPackages.plus(applicationId))
        setAppHidden(context)
        if (KioskUtils.isDeviceOwner(context)) {
            if (context is Activity) {
                context.startLockTask()
                Log.d("App", "LockTask模式启动成功")
            }
        } else {
            // 如果不是设备所有者，尝试基础模式
            enableBasicLockTaskMode(context, applicationId)
        }
    } catch (e: Exception) {
        Log.e("App", "启动LockTask失败", e)
        enableBasicLockTaskMode(context, applicationId)
    }
}

/**
 * 设置需要冻结的包名
 */
fun setAppHidden(context: Context) {
    val packageNames = arrayOf("com.hihonor.baidu.browser")
    packageNames.forEach { pkg ->
        KioskUtils.setAppHidden(context, pkg, true)
    }
}

/**
 * 基础锁定任务模式（备用方案）
 */
fun Application.enableBasicLockTaskMode(context: Context, applicationId: String) {
    try {
        val dpm = context.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin: ComponentName = ComponentName(context, MyDeviceAdminReceiver::class.java)
        val newPackages = KioskWhitelistManager.getInstance(context).getWhitelistAppsForKiosk()
        // 设置允许在LockTask模式中运行的应用白名单（仅当前应用）
        dpm.setLockTaskPackages(admin, newPackages.plus(applicationId))

        // 检查是否允许进入LockTask模式，如果允许则启动
        if (dpm.isLockTaskPermitted(applicationId)) {
            if (context is Activity) {
                context.startLockTask()
                Log.d("App", "基础LockTask模式启动成功")
            }
        }
    } catch (e: Exception) {
        Log.e("App", "基础LockTask模式启动失败", e)
    }
}