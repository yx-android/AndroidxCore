package com.haofenshu.lnkscreen

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log

object KioskUtils {
    private const val TAG = "KioskUtils"

    /**
     * 获取设备管理组件
     */
    private fun getDeviceAdminComponent(context: Context): ComponentName {
        return ComponentName(context, MyDeviceAdminReceiver::class.java)
    }

    /**
     * 安全设置全局参数，确保单个设置项失败不会中断其他流程
     */
    private fun safeSetGlobalSetting(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        setting: String,
        value: String
    ) {
        try {
            dpm.setGlobalSetting(admin, setting, value)
        } catch (e: Exception) {
            Log.w(TAG, "设置全局参数失败 [$setting]: ${e.message}")
        }
    }

    fun setupEnhancedKioskMode(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) {
                Log.w(TAG, "需要设备所有者权限")
                return false
            }

            // 1. 设置白名单应用
            refreshLockTaskPackages(context)

            // 2. 配置锁定任务特性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val features = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                devicePolicyManager.setLockTaskFeatures(adminComponent, features)
            }

            // 3. 用户限制
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)

            // 4. 屏蔽特定设置
            restrictSpecificSettings(context, devicePolicyManager, adminComponent)

            // 5. 设置屏幕常亮 (改用安全设置)
            safeSetGlobalSetting(
                devicePolicyManager,
                adminComponent,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                (BatteryManager.BATTERY_PLUGGED_AC or
                        BatteryManager.BATTERY_PLUGGED_USB or
                        BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
            )

            // 6. 禁用锁屏
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)

            Log.d(TAG, "增强Kiosk模式配置完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置增强Kiosk模式失败", e)
            false
        }
    }

    fun addAppToWhitelist(context: Context, packageName: String): Boolean {
        return addAppsToWhitelist(context, listOf(packageName))
    }

    fun addAppsToWhitelist(context: Context, packageNames: Collection<String>): Boolean {
        return try {
            val whitelistManager = KioskWhitelistManager.getInstance(context)
            val result = whitelistManager.addToWhitelist(packageNames)
            if (result && isKioskModeActive(context)) {
                refreshLockTaskPackages(context)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "批量添加白名单失败", e)
            false
        }
    }

    fun removeAppFromWhitelist(context: Context, packageName: String): Boolean {
        return removeAppsFromWhitelist(context, listOf(packageName))
    }

    fun removeAppsFromWhitelist(context: Context, packageNames: Collection<String>): Boolean {
        return try {
            val whitelistManager = KioskWhitelistManager.getInstance(context)
            val result = whitelistManager.removeFromWhitelist(packageNames)
            if (result && isKioskModeActive(context)) {
                refreshLockTaskPackages(context)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "批量移除白名单失败", e)
            false
        }
    }

    fun isAppInWhitelist(context: Context, packageName: String): Boolean {
        return try {
            KioskWhitelistManager.getInstance(context).isInWhitelist(packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun getAllWhitelistApps(context: Context): Set<String> {
        return try {
            KioskWhitelistManager.getInstance(context).getAllWhitelistPackages()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun getWhitelistStats(context: Context): String {
        return try {
            KioskWhitelistManager.getInstance(context).getWhitelistStats()
        } catch (e: Exception) {
            "获取统计信息失败"
        }
    }

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isKioskModeActive(context: Context): Boolean {
        return try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            if (isDeviceOwner(context)) {
                devicePolicyManager.getLockTaskPackages(adminComponent).isNotEmpty()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun refreshLockTaskPackages(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val whitelistManager = KioskWhitelistManager.getInstance(context)

            val newPackages = whitelistManager.getWhitelistAppsForKiosk()
            val currentPackages = devicePolicyManager.getLockTaskPackages(adminComponent)

            if (newPackages.toSet() == currentPackages.toSet()) {
                return true
            }

            devicePolicyManager.setLockTaskPackages(adminComponent, newPackages)
            true
        } catch (e: Exception) {
            Log.e(TAG, "刷新锁定任务包失败", e)
            false
        }
    }

    fun disableKioskMode(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false

            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)

            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                devicePolicyManager.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }

            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_CREDENTIALS)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BRIGHTNESS)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

            devicePolicyManager.setPermittedAccessibilityServices(adminComponent, null)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            devicePolicyManager.setKeyguardDisabled(adminComponent, false)

            Log.d(TAG, "Kiosk模式已禁用")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getKioskStatus(context: Context): String {
        val sb = StringBuilder("Kiosk模式状态:\n")
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

    fun openSystemSettings(context: Context, action: String = Settings.ACTION_SETTINGS): Boolean {
        return try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            if (action != Settings.ACTION_SETTINGS) openSystemSettings(context) else false
        }
    }

    fun openAppDetails(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun getNetworkStatusInfo(context: Context): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val sb = StringBuilder("网络状态信息:\n")
            if (network == null || capabilities == null) {
                sb.append("- 状态: 无网络连接\n")
            } else {
                val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                sb.append("- 状态: ${if (isConnected) "已连接" else "未连接"}\n")
                val type = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
                    else -> "其他"
                }
                sb.append("- 类型: $type\n")
            }
            sb.toString()
        } catch (e: Exception) {
            "获取网络状态信息失败"
        }
    }

    private fun restrictSpecificSettings(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)

            // 屏蔽分屏/多窗口全局设置
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "multi_window_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "force_resizable_activities", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "enable_freeform_support", "0")

            blockHonorResetSettings(context, devicePolicyManager, adminComponent)
            restrictSmartWindowFeatures(context, devicePolicyManager, adminComponent)
        } catch (e: Exception) {
            Log.e(TAG, "屏蔽特定设置失败", e)
        }
    }

    fun isSettingRestricted(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            devicePolicyManager.getUserRestrictions(adminComponent).getBoolean(UserManager.DISALLOW_CONFIG_CREDENTIALS, false)
        } catch (e: Exception) {
            false
        }
    }

    fun enableAppUpdate(context: Context, packageName: String? = null): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            devicePolicyManager.setUninstallBlocked(adminComponent, packageName ?: context.packageName, false)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun canAppBeUpdated(context: Context, packageName: String? = null): Boolean {
        return try {
            if (!isDeviceOwner(context)) return true
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, packageName ?: context.packageName)
            val hasInstallRestriction = devicePolicyManager.getUserRestrictions(adminComponent).getBoolean(UserManager.DISALLOW_INSTALL_APPS, false)
            !isBlocked && !hasInstallRestriction
        } catch (e: Exception) {
            true
        }
    }

    private fun restrictSmartWindowFeatures(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            blockHonorDockBarFloat(context, devicePolicyManager, adminComponent)
            val installedServices = getInstalledAccessibilityServices(context)
            val blockedPatterns = listOf(
                "multiwindow", "smartwindow", "splitscreen", "freeform", "floating", "float",
                "hihonor.smartshot", "hihonor.smartwindow", "hihonor.multiwindow",
                "hihonor.hndockbar", "hndockbar", "dock", "sidebar", "edge", "swipe"
            )
            val permittedServices = installedServices.filter { service ->
                blockedPatterns.none { pattern -> service.contains(pattern, ignoreCase = true) }
            }
            devicePolicyManager.setPermittedAccessibilityServices(adminComponent, permittedServices)
        } catch (e: Exception) {}
    }

    fun isSmartWindowBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val permittedServices = devicePolicyManager.getPermittedAccessibilityServices(adminComponent) ?: return false
            val blockedPatterns = listOf("multiwindow", "smartwindow", "split", "floating", "float")
            !permittedServices.any { service -> blockedPatterns.any { pattern -> service.contains(pattern, ignoreCase = true) } }
        } catch (e: Exception) {
            false
        }
    }

    fun shutdownDevice(context: Context): Boolean {
        return try {
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                .putExtra("android.intent.extra.KEY_CONFIRM", false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            rebootDevice(context)
        }
    }

    fun rebootDevice(context: Context): Boolean {
        return try {
            if (isDeviceOwner(context)) {
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                devicePolicyManager.reboot(getDeviceAdminComponent(context))
                true
            } else {
                context.sendBroadcast(Intent(Intent.ACTION_REBOOT).putExtra("nowait", 1))
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun blockHonorDockBarFloat(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            val blockedPackages = listOf(
                "com.hihonor.hndockbar", "com.huawei.hwdockbar",
                "com.hihonor.desktop.explorer", "com.hihonor.android.projectmenu"
            )
            blockedPackages.forEach { pkg ->
                try { devicePolicyManager.setApplicationHidden(adminComponent, pkg, true) } catch (e: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.addUserRestriction(adminComponent, "no_side_gestures")
            }
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "edge_gestures_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "honor_dock_bar_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "multi_window_menu_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "is_short_side_gesture_enabled", "0")
        } catch (e: Exception) {}
    }

    fun isHonorDockBarBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val dockPackages = listOf("com.huawei.hwdockbar", "com.hihonor.hndockbar", "com.hihonor.desktop.explorer")
            dockPackages.any { pkg -> devicePolicyManager.isApplicationHidden(adminComponent, pkg) }
        } catch (e: Exception) {
            false
        }
    }

    private fun blockHonorResetSettings(
        context: Context,
        devicePolicyManager: DevicePolicyManager,
        adminComponent: ComponentName
    ) {
        try {
            val bundle = Bundle().apply {
                putBoolean("no_factory_reset", true)
                putBoolean("no_network_reset", true)
                putBoolean("no_reset_settings", true)
                putBoolean("no_honor_reset", true)
                putBoolean("no_subsettings_reset", true)
            }
            devicePolicyManager.setApplicationRestrictions(adminComponent, "com.android.settings", bundle)
            
            // 使用 safeSetGlobalSetting 确保单个失败不影响后续
            safeSetGlobalSetting(devicePolicyManager, adminComponent, Settings.Global.DEVICE_PROVISIONED, "1")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "honor_factory_reset_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "honor_network_reset_enabled", "0")
            safeSetGlobalSetting(devicePolicyManager, adminComponent, "honor_reset_settings_enabled", "0")
            
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_NETWORK_RESET)
        } catch (e: Exception) {}
    }

    fun isHonorResetSettingsBlocked(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getDeviceAdminComponent(context)
            val restrictions = devicePolicyManager.getUserRestrictions(adminComponent)
            val hasUserRestrictions = restrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET, false) ||
                    restrictions.getBoolean(UserManager.DISALLOW_NETWORK_RESET, false)

            val settingsRestrictions = devicePolicyManager.getApplicationRestrictions(adminComponent, "com.android.settings")
            val appRestrictionsSet = settingsRestrictions.getBoolean("no_factory_reset", false) ||
                    settingsRestrictions.getBoolean("no_honor_reset", false)
            hasUserRestrictions || appRestrictionsSet
        } catch (e: Exception) {
            false
        }
    }

    private fun getInstalledAccessibilityServices(context: Context): List<String> {
        return try {
            context.packageManager.queryIntentServices(Intent("android.accessibilityservice.AccessibilityService"), PackageManager.GET_META_DATA)
                .map { it.serviceInfo.let { info -> ComponentName(info.packageName, info.name).flattenToString() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearBrightnessRestriction(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            devicePolicyManager.clearUserRestriction(getDeviceAdminComponent(context), UserManager.DISALLOW_CONFIG_BRIGHTNESS)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isBrightnessRestricted(context: Context): Boolean {
        return try {
            if (!isDeviceOwner(context)) return false
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            devicePolicyManager.getUserRestrictions(getDeviceAdminComponent(context)).getBoolean(UserManager.DISALLOW_CONFIG_BRIGHTNESS, false)
        } catch (e: Exception) {
            false
        }
    }
}
