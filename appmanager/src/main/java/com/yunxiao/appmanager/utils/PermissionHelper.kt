package com.yunxiao.appmanager.utils

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/**
 * 权限检查和引导工具类
 * 主要处理悬浮窗权限
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    /**
     * 检查是否有悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true  // Android 6.0以下默认有权限
        }
    }

    /**
     * 检查是否有应用使用统计权限（可选）
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(context: Context) {
        if (hasOverlayPermission(context)) {
            return
        }

        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            Toast.makeText(context, "请在设置中开启悬浮窗权限", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "打开悬浮窗权限设置失败", e)
            showPermissionGuideDialog(context)
        }
    }

    /**
     * 请求应用使用统计权限（可选）
     */
    fun requestUsageStatsPermission(context: Context) {
        if (hasUsageStatsPermission(context)) {
            return
        }

        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            Toast.makeText(context, "请在设置中开启应用使用统计权限", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "打开应用使用统计权限设置失败", e)
        }
    }

    /**
     * 显示权限引导对话框
     */
    fun showPermissionGuideDialog(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("权限授权")
            .setMessage("为了正常使用应用管理功能，需要以下权限：\n\n" +
                    "1. 悬浮窗权限 - 用于创建最小化悬浮窗\n" +
                    "2. 应用使用统计权限（可选）- 用于获取更详细的应用信息")
            .setPositiveButton("去设置") { _, _ ->
                openSystemSettings(context)
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    /**
     * 显示悬浮窗权限专门引导
     */
    fun showOverlayPermissionGuide(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("悬浮窗权限")
            .setMessage("应用需要悬浮窗权限来：\n\n" +
                    "• 创建最小化悬浮点维持服务运行\n" +
                    "• 在后台监听应用安装事件\n\n" +
                    "权限用途：\n" +
                    "创建一个1像素大小的透明悬浮点，几乎不可见，仅用于维持服务在后台运行")
            .setPositiveButton("去开启权限") { _, _ ->
                requestOverlayPermission(context)
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): Boolean {
        return hasOverlayPermission(context)
    }

    /**
     * 获取缺失权限的提示信息
     */
    fun getMissingPermissionMessage(context: Context): String {
        val missingPermissions = mutableListOf<String>()

        if (!hasOverlayPermission(context)) {
            missingPermissions.add("悬浮窗权限")
        }

        if (missingPermissions.isEmpty()) {
            return "所有权限已授权"
        }

        return "缺少权限：${missingPermissions.joinToString("、")}"
    }

    /**
     * 请求所有缺失权限
     */
    fun requestAllPermissions(context: Context) {
        if (!hasOverlayPermission(context)) {
            showOverlayPermissionGuide(context)
        }
    }

    /**
     * 打开系统设置页面
     */
    private fun openSystemSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开系统设置失败", e)
            Toast.makeText(context, "请手动在设置中授权", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查权限是否需要引导
     */
    fun needsPermissionGuide(context: Context): Boolean {
        return !hasOverlayPermission(context)
    }

    /**
     * 获取权限状态详细信息
     */
    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "悬浮窗权限" to hasOverlayPermission(context),
            "应用使用统计权限" to hasUsageStatsPermission(context)
        )
    }

    /**
     * 显示权限状态
     */
    fun showPermissionStatus(context: Context) {
        val status = getPermissionStatus(context)
        val statusText = status.entries.joinToString("\n") { (name, granted) ->
            "$name: ${if (granted) "✓ 已授权" else "✗ 未授权"}"
        }

        AlertDialog.Builder(context)
            .setTitle("权限状态")
            .setMessage(statusText)
            .setPositiveButton("确定", null)
            .setNeutralButton("设置权限") { _, _ ->
                requestAllPermissions(context)
            }
            .show()
    }
}