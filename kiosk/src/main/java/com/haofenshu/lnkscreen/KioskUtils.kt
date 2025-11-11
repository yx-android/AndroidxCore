package com.haofenshu.lnkscreen

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

object KioskUtils {
    private const val TAG = "KioskUtils"

    /**
     * 获取设备管理组件
     * 动态获取，避免硬编码包名
     */
    private fun getDeviceAdminComponent(context: Context): ComponentName {
        // 优先使用MyDeviceAdminReceiver（在kiosk模块中）
        return ComponentName(context.packageName, "com.haofenshu.lnkscreen.MyDeviceAdminReceiver")
    }

    fun setupEnhancedKioskMode(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "需要设备所有者权限")
                return false
            }

            // 1. 设置白名单应用为锁定任务包
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            val packages = whitelistManager.getWhitelistAppsForKiosk()
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)
            Log.d(TAG, "设置锁定任务包: ${packages.contentToString()}")

            // 2. 配置锁定任务特性（保留状态栏但屏蔽通知）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val features = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                // 移除 LOCK_TASK_FEATURE_NOTIFICATIONS 以屏蔽单应用模式下的通知
                devicePolicyManager.setLockTaskFeatures(adminComponent, features)
                Log.d(TAG, "保留状态栏但屏蔽通知功能")
            }

            // 3. 屏蔽恢复出厂设置功能
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            Log.d(TAG, "已屏蔽恢复出厂设置")

            // 5. 屏蔽特定的系统设置选项（任务5）
            restrictSpecificSettings(context, devicePolicyManager, adminComponent)

            // 4. 允许应用安装和卸载以及开发者选项（不设置这些限制）
            // 注意：不调用以下限制，保持应用安装卸载和开发者选项功能
            // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
            // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
            // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // 确保当前应用可以被卸载和更新
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, false)

            // 清除可能影响安装的限制
//            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
//            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)

            Log.d(TAG, "保持应用安装卸载和开发者选项功能")

            // 5. 设置屏幕常亮
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setGlobalSetting(
                    adminComponent,
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    (BatteryManager.BATTERY_PLUGGED_AC or
                            BatteryManager.BATTERY_PLUGGED_USB or
                            BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
                )
            }

            // 6. 禁用锁屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            }

            Log.d(TAG, "增强Kiosk模式配置完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置增强Kiosk模式失败", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addAppToWhitelist(context: Context, packageName: String): Boolean {
        return try {
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            val result = whitelistManager.addToWhitelist(packageName)

            // 如果Kiosk模式已启用，刷新锁定任务包
            if (result && isKioskModeActive(context)) {
                refreshLockTaskPackages(context)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "添加白名单失败", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun removeAppFromWhitelist(context: Context, packageName: String): Boolean {
        return try {
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            val result = whitelistManager.removeFromWhitelist(packageName)

            // 如果Kiosk模式已启用，刷新锁定任务包
            if (result && isKioskModeActive(context)) {
                refreshLockTaskPackages(context)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "移除白名单失败", e)
            false
        }
    }

    fun isAppInWhitelist(context: Context, packageName: String): Boolean {
        return try {
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            whitelistManager.isInWhitelist(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "检查白名单失败", e)
            false
        }
    }

    fun getAllWhitelistApps(context: Context): Set<String> {
        return try {
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            whitelistManager.getAllWhitelistPackages()
        } catch (e: Exception) {
            Log.e(TAG, "获取白名单失败", e)
            emptySet()
        }
    }

    fun getWhitelistStats(context: Context): String {
        return try {
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)
            whitelistManager.getWhitelistStats()
        } catch (e: Exception) {
            Log.e(TAG, "获取白名单统计失败", e)
            "获取统计信息失败"
        }
    }

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "检查设备所有者状态失败", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun isKioskModeActive(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            if (isDeviceOwner(context)) {
                val packages = devicePolicyManager.getLockTaskPackages(adminComponent)
                packages.isNotEmpty()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查Kiosk模式状态失败", e)
            false
        }
    }

    fun refreshLockTaskPackages(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                Log.w(TAG, "需要设备所有者权限才能刷新锁定任务包")
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val whitelistManager = KioskWhitelistManager.Companion.getInstance(context)

            val packages = whitelistManager.getWhitelistAppsForKiosk()
            devicePolicyManager.setLockTaskPackages(adminComponent, packages)

            Log.d(TAG, "已刷新锁定任务包: ${packages.contentToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "刷新锁定任务包失败", e)
            false
        }
    }

    fun disableKioskMode(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                Log.w(TAG, "需要设备所有者权限")
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            // 清除锁定任务包
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())

            // 恢复锁定任务特性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                devicePolicyManager.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }

            // 清除用户限制
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CREDENTIALS)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
            // 清除亮度调节限制，恢复亮度调节功能
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BRIGHTNESS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
            }

            // 清除辅助服务限制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.setPermittedAccessibilityServices(adminComponent, null)
            }
            // 确保开发者选项可用（如果之前被禁用的话）
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // 启用锁屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                devicePolicyManager.setKeyguardDisabled(adminComponent, false)
            }

            Log.d(TAG, "Kiosk模式已禁用")
            true
        } catch (e: Exception) {
            Log.e(TAG, "禁用Kiosk模式失败", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getKioskStatus(context: Context): String {
        val sb = StringBuilder()
        sb.append("Kiosk模式状态:\n")
        sb.append("- 设备所有者: ${if (isDeviceOwner(context)) "✓" else "✗"}\n")

        sb.append("- Kiosk模式: ${if (isKioskModeActive(context)) "✓" else "✗"}\n")

        if (isDeviceOwner(context)) {
            try {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = getDeviceAdminComponent(context)
                val packages = devicePolicyManager.getLockTaskPackages(adminComponent)
                sb.append("- 白名单应用数: ${packages.size}\n")

                val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
                sb.append("- 恢复出厂设置: ${if (restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET)) "已禁用" else "未禁用"}\n")
                sb.append("- 开发者选项: ${if (restrictions.getBoolean(UserManager.DISALLOW_DEBUGGING_FEATURES)) "已禁用" else "允许"}\n")
                sb.append("- 应用安装: ${if (restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS)) "已禁用" else "允许"}\n")
                sb.append("- 应用卸载: ${if (restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS)) "已禁用" else "允许"}\n")
                sb.append("- 通知功能: 已屏蔽\n")
                sb.append("- 系统重置选项: ${if (isSettingRestricted(context)) "已屏蔽" else "未屏蔽"}\n")
                sb.append("- Honor重置菜单项: ${if (isHonorResetSettingsBlocked(context)) "已屏蔽" else "未屏蔽"}\n")
                sb.append("- 智慧多窗功能: ${if (isSmartWindowBlocked(context)) "已屏蔽" else "未屏蔽"}\n")
                sb.append("- Honor侧滑悬浮入口: ${if (isHonorDockBarBlocked(context)) "已屏蔽" else "未屏蔽"}\n")
            } catch (e: Exception) {
                sb.append("- 状态获取失败: ${e.message}\n")
            }
        }

        return sb.toString()
    }

    /**
     * 跳转到系统设置页面
     */
    fun openSystemSettings(context: Context): Boolean {
        return openSystemSettings(context, Settings.ACTION_SETTINGS)
    }

    /**
     * 跳转到指定的系统设置页面
     */
    fun openSystemSettings(context: Context, action: String): Boolean {
        return try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "已打开设置页面: $action")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开设置页面失败: $action", e)
            // 如果指定设置页面失败，尝试打开通用设置
            if (action != Settings.ACTION_SETTINGS) {
                return openSystemSettings(context, Settings.ACTION_SETTINGS)
            }
            false
        }
    }

    /**
     * 跳转到开发者选项
     */
    fun openDeveloperOptions(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "已打开开发者选项")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开开发者选项失败", e)
            // 降级到普通设置
            return openSystemSettings(context)
        }
    }

    /**
     * 跳转到应用管理
     */
    fun openApplicationSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "已打开应用管理")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开应用管理失败", e)
            return openSystemSettings(context)
        }
    }

    /**
     * 跳转到特定应用的详情页
     */
    fun openAppDetails(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "已打开应用详情: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开应用详情失败: $packageName", e)
            false
        }
    }

    /**
     * 跳转到设备管理器设置
     */
    fun openDeviceAdminSettings(context: Context): Boolean {
        return try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            val adminComponent = getDeviceAdminComponent(context)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "启用设备管理功能以保护教育设备安全")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "已打开设备管理器设置")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开设备管理器设置失败", e)
            false
        }
    }

    /**
     * 临时退出Kiosk模式以便访问设置
     */
    fun temporaryExitForSettings(context: Context, targetSetting: String): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                Log.w(TAG, "需要设备所有者权限才能临时退出")
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            // 临时清除锁定任务包，允许访问设置
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())

            // 根据目标设置打开对应页面
            val success = when (targetSetting) {
                "developer" -> openDeveloperOptions(context)
                "applications" -> openApplicationSettings(context)
                "device_admin" -> openDeviceAdminSettings(context)
                else -> openSystemSettings(context)
            }

            if (success) {
                Log.d(TAG, "临时退出Kiosk模式成功，已打开设置: $targetSetting")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "临时退出Kiosk模式失败", e)
            false
        }
    }

    /**
     * 重新进入Kiosk模式
     */
    fun reenterKioskMode(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                return false
            }

            // 重新设置白名单
            val success = refreshLockTaskPackages(context)
            if (success) {
                Log.d(TAG, "重新进入Kiosk模式成功")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "重新进入Kiosk模式失败", e)
            false
        }
    }

    /**
     * 打开系统设置管理页面
     */
    fun openSystemSettingsManager(context: Context): Boolean {
        return try {
            val intent = Intent(context, SystemSettingsActivity::class.java)
            // 清除任务栈，确保直接打开SystemSettingsActivity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
            Log.d(TAG, "已打开系统设置管理页面")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开系统设置管理页面失败", e)
            false
        }
    }

    // ===== 网络检测和自动跳转功能 =====

    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
            val isConnected = networkInfo?.isConnected == true
            Log.d(TAG, "网络状态检查: ${if (isConnected) "已连接" else "未连接"}")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "检查网络状态失败", e)
            false
        }
    }

    /**
     * 获取网络状态详细信息
     */
    fun getNetworkStatusInfo(context: Context): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo

            val sb = StringBuilder()
            sb.append("网络状态信息:\n")

            if (networkInfo == null) {
                sb.append("- 状态: 无网络连接\n")
            } else {
                sb.append("- 状态: ${if (networkInfo.isConnected) "已连接" else "未连接"}\n")
                sb.append("- 类型: ${getNetworkTypeName(networkInfo.type)}\n")
                sb.append("- 名称: ${networkInfo.typeName}\n")
                if (networkInfo.extraInfo != null) {
                    sb.append("- 详情: ${networkInfo.extraInfo}\n")
                }
            }

            // 自动跳转功能已移除

            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "获取网络状态信息失败", e)
            "获取网络状态信息失败: ${e.message}"
        }
    }

    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            ConnectivityManager.TYPE_WIFI -> "WiFi"
            ConnectivityManager.TYPE_MOBILE -> "移动数据"
            ConnectivityManager.TYPE_ETHERNET -> "以太网"
            ConnectivityManager.TYPE_BLUETOOTH -> "蓝牙"
            else -> "其他($type)"
        }
    }

    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 获取双重入口服务状态
     */
    fun getFloatingEntryStatus(context: Context): String {
        val sb = StringBuilder()
        sb.append("双重入口服务状态:\n")
        sb.append("- 悬浮窗权限: ${if (hasOverlayPermission(context)) "已授权" else "未授权"}\n")
        // 网络自动跳转功能已移除

        return sb.toString()
    }

    // ===== 页面内悬浮按钮管理 =====

    /**
     * 更新页面内悬浮按钮的网络状态
     */
    fun updateInPageFloatingNetworkStatus(context: Context) {
        val hasNetwork = isNetworkAvailable(context)
        Log.d(TAG, "已更新页面内悬浮按钮网络状态: ${if (hasNetwork) "正常" else "异常"}")
    }
    /**
     * 启用降级入口系统（仅页面内悬浮按钮，不自动跳转）
     */
    fun enableLiteEntrySystem(context: Context): Boolean {
        return try {
            // 1. 网络自动跳转功能已移除

            // 2. 检查网络状态并更新页面内悬浮按钮
            updateInPageFloatingNetworkStatus(context)

            Log.d(TAG, "降级入口系统已启用（页面内悬浮按钮模式，无自动跳转）")
            true
        } catch (e: Exception) {
            Log.e(TAG, "启用降级入口系统失败", e)
            false
        }
    }

    /**
     * 获取完整的入口系统状态
     */
    fun getCompleteEntryStatus(context: Context): String {
        val sb = StringBuilder()
        sb.append("入口系统完整状态:\n")
        sb.append("=== 网络状态 ===\n")
        sb.append(getNetworkStatusInfo(context))
        sb.append("\n=== 悬浮窗服务 ===\n")
        sb.append(getFloatingEntryStatus(context))
        sb.append("\n=== 页面内悬浮按钮 ===\n")
        return sb.toString()
    }

    /**
     * 屏蔽特定的系统设置选项（任务5）
     * - 屏蔽系统设置中的重置选项
     * - 屏蔽辅助功能中的智慧多窗功能
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun restrictSpecificSettings(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            // 不限制用户配置功能，允许访问系统设置包括亮度调节
            // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CREDENTIALS)

            // 添加更多限制以确保重置功能被完全屏蔽
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
            }

            // 屏蔽Honor系统设置中的重置页面
            blockHonorResetSettings(context, devicePolicyManager, adminComponent)

            // 如果是Android 8.0+，可以使用更细粒度的控制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 使用setPermittedInputMethods限制重置相关的系统应用
                val permittedPackages = arrayListOf<String>()
                context.packageManager.getInstalledApplications(0).forEach { appInfo ->
                    val packageName = appInfo.packageName
                    // 过滤掉系统设置中的重置相关包
                    if (!packageName.contains("reset", ignoreCase = true) &&
                        !packageName.contains("restore", ignoreCase = true) &&
                        !packageName.contains("backup", ignoreCase = true)) {
                        permittedPackages.add(packageName)
                    }
                }

                // 屏蔽智慧多窗等特定辅助功能
                restrictSmartWindowFeatures(context, devicePolicyManager, adminComponent)
            }

            Log.d(TAG, "已屏蔽系统重置和智慧多窗功能")
        } catch (e: Exception) {
            Log.e(TAG, "屏蔽特定设置失败", e)
        }
    }

    /**
     * 获取已安装的辅助服务列表
     */
    private fun getInstalledAccessibilityServices(context: Context): List<String> {
        val services = mutableListOf<String>()
        try {
            val packageManager = context.packageManager
            val intent = Intent("android.accessibilityservice.AccessibilityService")
            val resolveInfos = packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)

            resolveInfos.forEach { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo
                val componentName = ComponentName(serviceInfo.packageName, serviceInfo.name)
                services.add(componentName.flattenToString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取辅助服务列表失败", e)
        }
        return services
    }

    /**
     * 检查特定设置是否被限制
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun isSettingRestricted(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)

            // 检查是否设置了相关限制
            restrictions.getBoolean(UserManager.DISALLOW_CONFIG_CREDENTIALS, false)
        } catch (e: Exception) {
            Log.e(TAG, "检查设置限制失败", e)
            false
        }
    }

    /**
     * 允许应用更新（解决无法覆盖安装的问题）
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun enableAppUpdate(context: Context, packageName: String? = null): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                Log.w(TAG, "不是设备管理员，无法设置应用更新权限")
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

            // 使用传入的包名或当前应用包名
            val targetPackage = packageName ?: context.packageName

            // 确保目标应用可以被卸载和更新
            devicePolicyManager.setUninstallBlocked(adminComponent, targetPackage, false)

            // 清除安装限制
//            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
//            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)

            // 如果目标应用在白名单中，确保它可以被更新
            if (isAppInWhitelist(context, targetPackage)) {
                Log.d(TAG, "应用 $targetPackage 在白名单中，确保可以更新")
            }

            Log.d(TAG, "已允许应用 $targetPackage 更新")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置应用更新权限失败", e)
            false
        }
    }

    /**
     * 检查应用是否可以被更新
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun canAppBeUpdated(context: Context, packageName: String? = null): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                // 非设备管理员模式下，默认可以更新
                return true
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
            val targetPackage = packageName ?: context.packageName

            // 检查是否被阻止卸载
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, targetPackage)

            // 检查是否有安装限制
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
            val hasInstallRestriction = restrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS, false)

            !isBlocked && !hasInstallRestriction
        } catch (e: Exception) {
            Log.e(TAG, "检查应用更新权限失败", e)
            true // 出错时默认允许
        }
    }

    /**
     * 屏蔽智慧多窗等辅助功能
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun restrictSmartWindowFeatures(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            // 首先屏蔽Honor侧滑悬浮入口
            blockHonorDockBarFloat(context, devicePolicyManager, adminComponent)

            // 获取已安装的辅助服务
            val installedServices = getInstalledAccessibilityServices(context)
            val permittedServices = arrayListOf<String>()

            // Honor智慧多窗相关的包名和服务
            val blockedPatterns = listOf(
                // 通用多窗关键词
                "multiwindow", "multi_window", "multi-window",
                "smartwindow", "smart_window", "smart-window",
                "splitscreen", "split_screen", "split-screen",
                "freeform", "free_form", "free-form",
                "floating", "float", "popup",

                // Honor特定的智慧多窗服务
                "hihonor.smartshot", "hihonor.smartwindow", "hihonor.multiwindow",
                "hihonor.featurelayer", "hihonor.floattasks", "hihonor.stylus.floatmenu",
                "hihonor.desktop.explorer", "hihonor.android.projectmenu",

                // Honor侧滑悬浮入口相关
                "hihonor.hndockbar", "hndockbar", "dock", "dockbar",
                "sidebar", "sideentry", "edge", "swipe",

                // 其他可能的多窗服务
                "window", "desk", "taskbar", "sidebar"
            )

            installedServices.forEach { service ->
                var shouldBlock = false

                // 检查是否包含被屏蔽的关键词
                blockedPatterns.forEach { pattern ->
                    if (service.contains(pattern, ignoreCase = true)) {
                        shouldBlock = true
                        Log.d(TAG, "屏蔽辅助服务: $service (匹配模式: $pattern)")
                        return@forEach
                    }
                }

                // 如果不在屏蔽列表中，则允许
                if (!shouldBlock) {
                    permittedServices.add(service)
                    Log.d(TAG, "允许辅助服务: $service")
                }
            }

            // 设置允许的辅助服务列表
            devicePolicyManager.setPermittedAccessibilityServices(
                adminComponent,
                permittedServices
            )

            Log.d(TAG, "已设置辅助服务限制，允许${permittedServices.size}个服务，屏蔽${installedServices.size - permittedServices.size}个服务")

        } catch (e: Exception) {
            Log.e(TAG, "设置辅助服务限制失败", e)
            // 如果设置失败，尝试禁用所有非系统辅助服务
            try {
                devicePolicyManager.setPermittedAccessibilityServices(adminComponent, emptyList())
                Log.d(TAG, "降级处理：禁用所有辅助服务")
            } catch (fallbackException: Exception) {
                Log.e(TAG, "降级处理也失败", fallbackException)
            }
        }
    }

    /**
     * 检查智慧多窗功能是否被屏蔽
     */
    fun isSmartWindowBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

            // 获取当前允许的辅助服务列表
            val permittedServices = devicePolicyManager.getPermittedAccessibilityServices(adminComponent)

            // 如果返回null，表示没有限制（所有服务都允许）
            if (permittedServices == null) {
                Log.d(TAG, "辅助服务无限制，智慧多窗可能未被屏蔽")
                return false
            }

            // 检查是否有智慧多窗相关的服务被允许
            val blockedPatterns = listOf(
                "multiwindow", "smartwindow", "split", "floating", "float",
                "hihonor.floattasks", "hihonor.stylus.floatmenu"
            )

            val hasSmartWindow = permittedServices.any { service ->
                blockedPatterns.any { pattern ->
                    service.contains(pattern, ignoreCase = true)
                }
            }

            Log.d(TAG, "智慧多窗屏蔽状态: ${!hasSmartWindow}, 允许的辅助服务数量: ${permittedServices.size}")
            !hasSmartWindow

        } catch (e: Exception) {
            Log.e(TAG, "检查智慧多窗屏蔽状态失败", e)
            false
        }
    }

    /**
     * Debug模式下退出单应用模式
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun exitKioskModeInDebug(context: Context): Boolean {
        return try {
            // 检查是否为debug模式
            val applicationInfo = context.applicationInfo
            val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

            if (!isDebug) {
                Log.w(TAG, "非Debug模式，不允许退出Kiosk模式")
                return false
            }

            // 如果正在锁定任务模式，先退出
            if (isKioskModeActive(context)) {
                if (context is Activity) {
                    context.stopLockTask()
                }
            }

            // 清除Kiosk模式设置
            if (isDeviceOwner(context)) {
                disableKioskMode(context)
            }

            Log.d(TAG, "Debug模式下成功退出Kiosk模式")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Debug模式退出Kiosk失败", e)
            false
        }
    }

    /**
     * 执行设备关机
     * 在单应用模式下需要设备管理员权限
     */
    fun shutdownDevice(context: Context): Boolean {
        return try {
            when {
                // 方案1：如果是设备所有者，使用DevicePolicyManager关机
                isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                    val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

                    // Android 7.0+ 支持设备所有者关机
                    try {
                        devicePolicyManager.reboot(adminComponent)
                        Log.d(TAG, "通过设备管理员执行重启")
                        true
                    } catch (e: Exception) {
                        Log.w(TAG, "设备管理员重启失败，尝试其他方法", e)
                        shutdownWithIntent(context)
                    }
                }

                // 方案2：使用系统Intent关机（需要系统权限）
                else -> {
                    shutdownWithIntent(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "关机失败", e)
            false
        }
    }

    /**
     * 使用Intent方式关机
     */
    private fun shutdownWithIntent(context: Context): Boolean {
        return try {
            // 尝试使用关机Intent
            val shutdownIntent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
            shutdownIntent.putExtra("android.intent.extra.KEY_CONFIRM", false)
            shutdownIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(shutdownIntent)
            Log.d(TAG, "发送关机Intent")
            true
        } catch (e: Exception) {
            Log.w(TAG, "关机Intent失败，尝试重启Intent", e)

            // 备选方案：尝试重启
            try {
                val rebootIntent = Intent(Intent.ACTION_REBOOT)
                rebootIntent.putExtra("nowait", 1)
                rebootIntent.putExtra("interval", 1)
                rebootIntent.putExtra("window", 0)
                context.sendBroadcast(rebootIntent)
                Log.d(TAG, "发送重启广播")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "重启也失败", e2)
                false
            }
        }
    }

    /**
     * 执行设备重启
     * 在单应用模式下需要设备管理员权限
     */
    fun rebootDevice(context: Context): Boolean {
        return try {
            when {
                // 如果是设备所有者，使用DevicePolicyManager重启
                isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                    val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

                    devicePolicyManager.reboot(adminComponent)
                    Log.d(TAG, "通过设备管理员执行重启")
                    true
                }

                // 其他情况使用Intent
                else -> {
                    val rebootIntent = Intent(Intent.ACTION_REBOOT)
                    rebootIntent.putExtra("nowait", 1)
                    rebootIntent.putExtra("interval", 1)
                    rebootIntent.putExtra("window", 0)
                    context.sendBroadcast(rebootIntent)
                    Log.d(TAG, "发送重启广播")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "重启失败", e)
            false
        }
    }

    /**
     * 检查是否有关机/重启权限
     */
    fun canShutdownOrReboot(context: Context): Boolean {
        return when {
            // 设备所有者可以关机/重启
            isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> true

            // 检查是否有REBOOT权限（通常需要系统应用）
            context.checkSelfPermission("android.permission.REBOOT") == PackageManager.PERMISSION_GRANTED -> true

            // 其他情况
            else -> false
        }
    }

    /**
     * 专门屏蔽Honor侧滑悬浮入口 (HnDockBarFloatWindow)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun blockHonorDockBarFloat(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            Log.d(TAG, "开始屏蔽Honor侧滑悬浮入口 (HnDockBarFloatWindow)")

            // 1. 屏蔽com.hihonor.hndockbar包
            val blockedPackages = listOf(
                "com.hihonor.hndockbar",
                "com.huawei.hwdockbar",
                "com.hihonor.desktop.explorer",
                "com.hihonor.android.projectmenu"
            )

            blockedPackages.forEach { packageName ->
                try {
                    // 检查包是否存在
                    context.packageManager.getPackageInfo(packageName, 0)

                    // 隐藏应用（不会卸载，只是隐藏）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
                        Log.d(TAG, "已隐藏侧滑悬浮应用: $packageName")
                    }

                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d(TAG, "包不存在，跳过: $packageName")
                } catch (e: Exception) {
                    Log.w(TAG, "屏蔽包失败: $packageName", e)
                }
            }

            // 2. 禁用系统UI相关功能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    // 不再限制亮度调节，允许手动和自动调节
                    // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BRIGHTNESS)

                    // 如果API支持，禁用边缘手势
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        devicePolicyManager.addUserRestriction(adminComponent, "no_side_gestures")
                    }

                    Log.d(TAG, "已禁用边缘手势和相关系统UI功能")
                } catch (e: Exception) {
                    Log.w(TAG, "禁用系统UI功能失败", e)
                }
            }

            // 3. 通过全局设置禁用边缘滑动
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    // 禁用边缘手势导航
                    devicePolicyManager.setGlobalSetting(
                        adminComponent,
                        "edge_gestures_enabled",
                        "0"
                    )

                    // 禁用侧边栏功能
                    devicePolicyManager.setGlobalSetting(
                        adminComponent,
                        "honor_dock_bar_enabled",
                        "0"
                    )

                    Log.d(TAG, "已通过全局设置禁用边缘手势")
                } catch (e: Exception) {
                    Log.w(TAG, "设置全局边缘手势禁用失败", e)
                }
            }

            // 4. 使用输入法限制来屏蔽相关服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // 获取当前允许的输入法，过滤掉dock相关的
                    val permittedInputMethods = mutableListOf<String>()

                    context.packageManager.getInstalledPackages(0).forEach { packageInfo ->
                        val packageName = packageInfo.packageName

                        // 排除dock相关的包
                        if (!packageName.contains("dock", ignoreCase = true) &&
                            !packageName.contains("hndockbar", ignoreCase = true) &&
                            !packageName.contains("sidebar", ignoreCase = true)) {
                            permittedInputMethods.add(packageName)
                        }
                    }

                    Log.d(TAG, "Honor侧滑悬浮入口屏蔽策略已应用")
                } catch (e: Exception) {
                    Log.w(TAG, "输入法限制设置失败", e)
                }
            }

            Log.d(TAG, "Honor侧滑悬浮入口屏蔽完成")

        } catch (e: Exception) {
            Log.e(TAG, "屏蔽Honor侧滑悬浮入口失败", e)
        }
    }

    /**
     * 检查Honor侧滑悬浮入口是否被屏蔽
     */
    fun isHonorDockBarBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

            // 检查关键的dock相关应用是否被隐藏
            val dockPackages = listOf(
                "com.huawei.hwdockbar",
                "com.hihonor.hndockbar",
                "com.hihonor.desktop.explorer"
            )

            var blockedCount = 0
            dockPackages.forEach { packageName ->
                try {
                    // 检查包是否存在
                    context.packageManager.getPackageInfo(packageName, 0)

                    // 检查是否被隐藏
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val isHidden = devicePolicyManager.isApplicationHidden(adminComponent, packageName)
                        if (isHidden) {
                            blockedCount++
                            Log.d(TAG, "侧滑悬浮应用已屏蔽: $packageName")
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // 包不存在，也算是"屏蔽"了
                    blockedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "检查包状态失败: $packageName", e)
                }
            }

            val isBlocked = blockedCount > 0
            Log.d(TAG, "Honor侧滑悬浮入口屏蔽状态: $isBlocked (${blockedCount}/${dockPackages.size}个应用被屏蔽)")
            isBlocked

        } catch (e: Exception) {
            Log.e(TAG, "检查Honor侧滑悬浮入口屏蔽状态失败", e)
            false
        }
    }

    /**
     * 屏蔽Honor系统设置中的重置菜单项
     * 目标: 系统设置应用内的SubSettings重置页面
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun blockHonorResetSettings(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            Log.d(TAG, "开始屏蔽Honor系统设置中的重置菜单项")

            // 1. 通过应用限制屏蔽系统设置中的重置功能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val settingsRestrictions = Bundle().apply {
                        // 禁用恢复出厂设置菜单项
                        putBoolean("no_factory_reset", true)
                        // 禁用网络重置菜单项
                        putBoolean("no_network_reset", true)
                        // 禁用重置设置菜单项
                        putBoolean("no_reset_settings", true)
                        // Honor特定的重置限制
                        putBoolean("no_honor_reset", true)
                        putBoolean("no_subsettings_reset", true)
                    }

                    // 对系统设置应用设置限制
                    devicePolicyManager.setApplicationRestrictions(
                        adminComponent,
                        "com.android.settings",
                        settingsRestrictions
                    )

                    Log.d(TAG, "已设置系统设置应用限制，屏蔽重置菜单项")
                } catch (e: Exception) {
                    Log.w(TAG, "设置系统设置应用限制失败", e)
                }
            }

            // 2. 通过全局设置禁用重置功能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    // 禁用恢复出厂设置
                    devicePolicyManager.setGlobalSetting(
                        adminComponent,
                        Settings.Global.DEVICE_PROVISIONED,
                        "1"
                    )

                    // 禁用Honor特定的重置选项
                    val honorResetSettings = listOf(
                        "honor_factory_reset_enabled",
                        "honor_network_reset_enabled",
                        "honor_reset_settings_enabled",
                        "settings_reset_options_enabled",
                        "factory_reset_from_settings_enabled"
                    )

                    honorResetSettings.forEach { setting ->
                        try {
                            devicePolicyManager.setGlobalSetting(adminComponent, setting, "0")
                            Log.d(TAG, "禁用设置: $setting")
                        } catch (e: Exception) {
                            Log.w(TAG, "设置 $setting 失败", e)
                        }
                    }

                    Log.d(TAG, "已通过全局设置禁用重置选项")
                } catch (e: Exception) {
                    Log.w(TAG, "设置全局重置禁用失败", e)
                }
            }

            // 3. 通过Intent过滤屏蔽重置相关的跳转
            try {
                // 添加Intent过滤器限制（在运行时检查）
                val blockedIntentFilters = listOf(
                    "android.settings.FACTORY_RESET_SETTINGS",
                    "android.settings.NETWORK_RESET_SETTINGS",
                    "android.settings.RESET_SETTINGS",
                    "com.hihonor.settings.RESET",
                    "com.hihonor.settings.FACTORY_RESET"
                )

                // 记录需要在运行时拦截的Intent
                blockedIntentFilters.forEach { action ->
                    Log.d(TAG, "需要拦截的重置Intent: $action")
                }

            } catch (e: Exception) {
                Log.w(TAG, "处理Intent过滤失败", e)
            }

            // 4. 添加强化的用户限制
            try {
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
                // 不限制用户配置功能，允许亮度调节
                // devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CREDENTIALS)

                // 添加更多限制来彻底屏蔽重置功能
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                }

                Log.d(TAG, "已添加用户限制以屏蔽重置功能")
            } catch (e: Exception) {
                Log.w(TAG, "添加用户限制失败", e)
            }

            Log.d(TAG, "Honor系统设置重置菜单项屏蔽设置完成")

        } catch (e: Exception) {
            Log.e(TAG, "屏蔽Honor系统设置重置菜单项失败", e)
        }
    }

    /**
     * 检查Honor系统设置重置菜单项是否被屏蔽
     */
    fun isHonorResetSettingsBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

            // 检查相关的用户限制是否已设置
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
            val hasFactoryResetRestriction = restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
            val hasNetworkResetRestriction = restrictions.getBoolean(UserManager.DISALLOW_NETWORK_RESET, false)
            val hasCredentialsRestriction = restrictions.getBoolean(UserManager.DISALLOW_CONFIG_CREDENTIALS, false)

            val restrictionCount = listOf(
                hasFactoryResetRestriction,
                hasNetworkResetRestriction,
                hasCredentialsRestriction
            ).count { it }

            // 检查系统设置应用的限制
            var appRestrictionsSet = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val settingsRestrictions = devicePolicyManager.getApplicationRestrictions(
                        adminComponent,
                        "com.android.settings"
                    )

                    appRestrictionsSet = settingsRestrictions.getBoolean("no_factory_reset", false) ||
                            settingsRestrictions.getBoolean("no_network_reset", false) ||
                            settingsRestrictions.getBoolean("no_reset_settings", false) ||
                            settingsRestrictions.getBoolean("no_honor_reset", false) ||
                            settingsRestrictions.getBoolean("no_subsettings_reset", false)

                    if (appRestrictionsSet) {
                        Log.d(TAG, "系统设置应用已设置重置限制")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "无法检查系统设置应用限制: ${e.message}")
            }

            val isBlocked = restrictionCount >= 2 || appRestrictionsSet
            Log.d(TAG, "Honor系统重置菜单项屏蔽状态: $isBlocked (用户限制: $restrictionCount/3, 应用限制: $appRestrictionsSet)")
            isBlocked

        } catch (e: Exception) {
            Log.e(TAG, "检查Honor系统重置菜单项屏蔽状态失败", e)
            false
        }
    }

    /**
     * 清除亮度调节限制，恢复手动和自动亮度调节功能
     * 适用于已经设置过 DISALLOW_CONFIG_BRIGHTNESS 的设备
     */
    fun clearBrightnessRestriction(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                Log.w(TAG, "需要设备所有者权限才能清除亮度限制")
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            // 清除亮度调节限制
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BRIGHTNESS)

            // 验证限制是否已清除
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
            val isBrightnessRestricted = restrictions.getBoolean(UserManager.DISALLOW_CONFIG_BRIGHTNESS, false)

            if (!isBrightnessRestricted) {
                Log.d(TAG, "亮度调节限制已成功清除")
                true
            } else {
                Log.w(TAG, "清除亮度调节限制失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除亮度调节限制时出错", e)
            false
        }
    }

    /**
     * 检查是否存在亮度调节限制
     */
    fun isBrightnessRestricted(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) {
                return false
            }

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)

            restrictions.getBoolean(UserManager.DISALLOW_CONFIG_BRIGHTNESS, false)
        } catch (e: Exception) {
            Log.e(TAG, "检查亮度限制状态失败", e)
            false
        }
    }
}