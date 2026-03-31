package com.haofenshu.lnkscreen

import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.DEVICE_POLICY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
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
                // 计划一：在启动LockTask模式之前，强行将当前Activity设置为系统默认桌面
                KioskUtils.setDefaultLauncher(context, context.componentName)

                context.startLockTask()
                Log.d("App", "LockTask模式且默认桌面设置启动成功")
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
 * 动态扫描并隐蔽（冻结）不要的应用：
 * 1. 冻结所有非系统的第三方应用（白名单应用除外）
 * 2. 冻结所有自带的系统级桌面/Launcher，防止抢占主屏
 * 3. 绝对放过普通的系统应用（如 com.android.settings）以防系统崩溃
 */
fun setAppHidden(context: Context) {
    try {
        val pm = context.packageManager
        val myPackageName = context.packageName

        // 1. 获取系统的白名单
        val whitelist = KioskWhitelistManager.getInstance(context).getAllWhitelistPackages()

        // 2. 利用底层 Shell 脱壳获取所有的第三方包与系统包
        val thirdPartyApps = KioskUtils.getPackagesByShell("-3").toSet()
        val systemApps = KioskUtils.getPackagesByShell("-s").toSet()
        val allPackages = thirdPartyApps + systemApps
        
        Log.d("setAppHidden", "========== [底层包扫描日志] ==========")
        Log.d("setAppHidden", "检测到 ${systemApps.size} 个系统包，${thirdPartyApps.size} 个第三方包")
        systemApps.forEach { Log.d("setAppHidden", " |- [System] $it") }
        thirdPartyApps.forEach { Log.d("setAppHidden", " |- [3rdParty] $it") }
        Log.d("setAppHidden", "======================================")

        // 3. 抓取被系统注册为桌面的包（动态捕捉桌面）
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val dynamicLaunchers = pm.queryIntentActivities(
            homeIntent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        ).map { it.activityInfo.packageName }.toSet()

        // 4. 抓取被系统注册为浏览器的包（动态捕捉所有浏览器，取代曾经硬性配置的 com.hihonor.baidu.browser）
        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://www.google.com"))
        val dynamicBrowsers = pm.queryIntentActivities(
            browserIntent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        ).map { it.activityInfo.packageName }.toSet()
        
        // 动态绞杀名单：完全由代码探测出的“所有桌面” + “所有浏览器”
        val destroyList = dynamicLaunchers + dynamicBrowsers

        // 5. 极高优先级的荣耀系统底层与特定组件的免死金牌名单（即使用户点出了这些，也绝不冻结以保稳定）
        val essentialHonorPackages = setOf(
            // --- 您刚才特别指出的核心层和桌面 ---
            "com.hihonor.motionservice", "com.hihonor.securityserver", "com.baidu.input_hihonor",
            "com.hihonor.filemanager", "com.hihonor.camera", "com.hihonor.photos",
            "com.hihonor.systemmanager", "com.hihonor.webview", "com.hihonor.android.wfdft",
            "com.hihonor.android.magicx.media.audioengine", "com.hihonor.systemserver",
        )

        allPackages.forEach { pkgName ->
            // 防御机制一：绝对不碰自己和Android最底层核心
            if (pkgName == myPackageName || pkgName == "android") return@forEach

            // 防御机制二：只要是 com.android. 开头，无脑绝对大赦放行！
            if (pkgName.startsWith("com.android.")) return@forEach

            // 防御机制三：特定的荣耀保底大赦名单，无视后面的猎杀判别，直接放行保护！
            if (essentialHonorPackages.contains(pkgName)) return@forEach

            // 绝对白名单放行
            if (whitelist.contains(pkgName)) return@forEach

            val isSystem = systemApps.contains(pkgName)
            val isDestructive = destroyList.contains(pkgName)

            // 【智能防御策略】:
            // - 如果它是原厂自带/第三方的桌面、浏览器？ 必杀！
            // - 如果它是第三方包（如通过某些手段自己装的杂乱应用）且没在您的白名单里？ 必杀！
            // - 如果它是系统底层的依赖包（如 com.android.systemui, com.android.settings）？ 放过保命！
            val shouldHide = isDestructive || !isSystem

            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
            } catch (e: Exception) {
                "不可见/未知"
            }

            Log.d("setAppHidden", "[冻结裁判] 名:$appName | 包:$pkgName | 系统包:$isSystem | 破坏项:$isDestructive | 冻结:$shouldHide")

            if (shouldHide) {
                // 执行隐藏隔离
                KioskUtils.setAppHidden(context, pkgName, true)
            }
        }
        
    } catch (e: Exception) {
        Log.e("App", "动态抓取并隐藏无关应用时出错", e)
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