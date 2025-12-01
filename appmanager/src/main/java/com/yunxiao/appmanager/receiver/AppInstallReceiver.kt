package com.yunxiao.appmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 应用安装监听广播接收器
 * 监听应用安装、更新、卸载事件
 */
class AppInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppInstallReceiver"
        private const val LAUNCH_DELAY = 2000L  // 延迟2秒启动，确保安装完成
        private const val MAX_RETRY_COUNT = 3   // 最大重试次数
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageName = getPackageName(intent)
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

                if (packageName != null && !isReplacing) {
                    Log.d(TAG, "应用安装成功: $packageName")
                    handleAppInstalled(context, packageName)
                }
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = getPackageName(intent)
                if (packageName != null) {
                    Log.d(TAG, "应用更新成功: $packageName")
                    handleAppUpdated(context, packageName)
                }
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = getPackageName(intent)
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

                if (packageName != null && !isReplacing) {
                    Log.d(TAG, "应用卸载: $packageName")
                    handleAppRemoved(context, packageName)
                }
            }
        }
    }

    /**
     * 获取包名
     */
    private fun getPackageName(intent: Intent): String? {
        return intent.data?.schemeSpecificPart
    }

    /**
     * 处理应用安装完成
     */
    private fun handleAppInstalled(context: Context, packageName: String) {
        // 检查是否为系统应用，如果是则忽略
        if (isSystemApp(context, packageName)) {
            Log.d(TAG, "跳过系统应用: $packageName")
            return
        }

        // 延迟启动应用，确保安装完全完成
        Handler(Looper.getMainLooper()).postDelayed({
            launchAppWithRetry(context, packageName)
        }, LAUNCH_DELAY)
    }

    /**
     * 处理应用更新
     */
    private fun handleAppUpdated(context: Context, packageName: String) {
        Log.d(TAG, "应用更新处理: $packageName")
        // 更新应用也可以选择启动，根据需求决定
        // launchAppWithRetry(context, packageName)
    }

    /**
     * 处理应用卸载
     */
    private fun handleAppRemoved(context: Context, packageName: String) {
        Log.d(TAG, "应用卸载处理: $packageName")
        // 可以在这里清理相关数据或状态
    }

    /**
     * 带重试机制的应用启动
     */
    private fun launchAppWithRetry(context: Context, packageName: String, retryCount: Int = 0) {
        try {
            val packageManager = context.packageManager

            // 检查应用是否安装完成且可用
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            if (!appInfo.enabled) {
                Log.w(TAG, "应用未启用: $packageName")
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.d(TAG, "重试启动应用 ($retryCount/$MAX_RETRY_COUNT): $packageName")
                    Handler(Looper.getMainLooper()).postDelayed({
                        launchAppWithRetry(context, packageName, retryCount + 1)
                    }, 1000L)
                } else {
                    Log.e(TAG, "启动应用失败，超过最大重试次数: $packageName")
                }
                return
            }

            // 获取启动Intent
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                context.startActivity(launchIntent)
                Log.d(TAG, "应用启动成功: $packageName")
            } else {
                Log.w(TAG, "无法获取启动Intent，应用可能没有主Activity: $packageName")
            }

        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "应用未找到: $packageName", e)
            if (retryCount < MAX_RETRY_COUNT) {
                Log.d(TAG, "重试启动应用 ($retryCount/$MAX_RETRY_COUNT): $packageName")
                Handler(Looper.getMainLooper()).postDelayed({
                    launchAppWithRetry(context, packageName, retryCount + 1)
                }, 1500L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用异常: $packageName", e)
        }
    }

    /**
     * 判断是否为系统应用
     */
    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            // 添加一些常见的系统应用包名过滤
            val systemPackages = setOf(
                "com.android.systemui",
                "com.google.android.gms",
                "com.android.vending",
                "com.google.android.gms",
                "android",
                "com.huawei.android.launcher",
                "com.miui.home",
                "com.oppo.launcher"
            )

            isSystemApp || systemPackages.contains(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "检查系统应用失败: $packageName", e)
            false
        }
    }

    /**
     * 检查应用是否可以启动
     */
    private fun isAppLaunchable(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent != null
        } catch (e: Exception) {
            false
        }
    }
}