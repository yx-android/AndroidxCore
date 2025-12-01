package com.yunxiao.appmanager

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yunxiao.appmanager.databinding.ActivityManageBinding
import com.yunxiao.appmanager.service.SilentMonitorService
import com.yunxiao.appmanager.utils.PermissionHelper

/**
 * 应用管理主界面
 * 提供权限检查、服务启动/停止、状态管理等功能
 */
class ManageActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ManageActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }

    // ViewBinding
    private lateinit var binding: ActivityManageBinding

    // 状态变量
    private var serviceRunning = false
    private var hasOverlayPermission = false

    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupClickListeners()
        checkPermissions()
        checkServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        // 重新检查权限状态
        checkPermissions()
        checkServiceStatus()
    }

    /**
     * 初始化视图组件
     */
    private fun initViews() {
        // 设置 Toolbar
        setSupportActionBar(binding.toolbar)

        // 调试模式下显示调试信息卡片
        binding.cardDebug.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
    }

    /**
     * 设置点击监听器
     */
    private fun setupClickListeners() {
        binding.btnRequestPermission.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        binding.btnStartService.setOnClickListener {
            startMonitorService()
        }

        binding.btnStopService.setOnClickListener {
            stopMonitorService()
        }
    }

    /**
     * 检查权限状态
     */
    private fun checkPermissions() {
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(this)
        updatePermissionUI()
    }

    /**
     * 检查服务状态
     */
    private fun checkServiceStatus() {
        // 这里可以通过其他方式检查服务是否运行
        // 简化处理，通过广播或SharedPreferences等机制判断
        updateServiceUI()
    }

    /**
     * 更新权限相关UI
     */
    private fun updatePermissionUI() {
        if (hasOverlayPermission) {
            binding.tvPermissionStatus.text = "✓ 已授权"
            binding.tvPermissionStatus.setTextColor(getColor(R.color.md_theme_light_primary))
            binding.btnRequestPermission.visibility = View.GONE

            // 更新卡片背景色
            binding.cardPermissions.setCardBackgroundColor(getColor(R.color.md_theme_light_primaryContainer))

            // 测试启动指定应用
        } else {
            binding.tvPermissionStatus.text = "未授权"
            binding.tvPermissionStatus.setTextColor(getColor(R.color.md_theme_light_error))
            binding.btnRequestPermission.visibility = View.VISIBLE

            // 更新卡片背景色
            binding.cardPermissions.setCardBackgroundColor(getColor(R.color.md_theme_light_errorContainer))
        }

        // 更新按钮状态
        binding.btnStartService.isEnabled = !serviceRunning && hasOverlayPermission
    }

    /**
     * 更新服务相关UI
     */
    private fun updateServiceUI() {
        if (serviceRunning) {
            binding.tvServiceStatus.text = "服务运行中"
            binding.tvServiceStatus.setTextColor(getColor(R.color.md_theme_light_primary))
            binding.btnStartService.isEnabled = false
            binding.btnStopService.isEnabled = true
        } else {
            binding.tvServiceStatus.text = "服务已停止"
            binding.tvServiceStatus.setTextColor(getColor(R.color.md_theme_light_error))
            binding.btnStartService.isEnabled = hasOverlayPermission
            binding.btnStopService.isEnabled = false
        }

        // 更新调试信息
        updateDebugInfo()
    }

    /**
     * 更新调试信息
     */
    private fun updateDebugInfo() {
        if (BuildConfig.DEBUG && binding.cardDebug.visibility == View.VISIBLE) {
            val targetPackageName = "com.jingzhunxue.tifenben"
            val isAppInstalled = isAppInstalled(targetPackageName)

            val debugText = buildString {
                append("=== 调试信息 ===\n")
                append("悬浮窗权限: ${if (hasOverlayPermission) "已授权" else "未授权"}\n")
                append("服务状态: ${if (serviceRunning) "运行中" else "已停止"}\n")
                append("测试应用包名: $targetPackageName\n")
                append("测试应用状态: ${if (isAppInstalled) "已安装" else "未安装"}\n")
                append("Android版本: ${android.os.Build.VERSION.RELEASE}\n")
                append("API级别: ${android.os.Build.VERSION.SDK_INT}\n")
                append("应用包名: ${packageName}\n")
                append("构建类型: ${if (BuildConfig.DEBUG) "Debug" else "Release"}\n")
            }
            binding.tvDebugInfo.text = debugText
        }
    }

    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 启动监控服务
     */
    private fun startMonitorService() {
        if (!hasOverlayPermission) {
            Log.w(TAG, "缺少悬浮窗权限，无法启动服务")
            PermissionHelper.showOverlayPermissionGuide(this)
            return
        }

        try {
            val intent = Intent(this, SilentMonitorService::class.java).apply {
                action = SilentMonitorService.ACTION_START_SERVICE
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            serviceRunning = true
            updateServiceUI()
            Log.d(TAG, "监控服务启动成功")

        } catch (e: Exception) {
            Log.e(TAG, "启动监控服务失败", e)
            serviceRunning = false
            updateServiceUI()
        }
    }

    /**
     * 停止监控服务
     */
    private fun stopMonitorService() {
        try {
            val intent = Intent(this, SilentMonitorService::class.java).apply {
                action = SilentMonitorService.ACTION_STOP_SERVICE
            }
            startService(intent)

            serviceRunning = false
            updateServiceUI()
            Log.d(TAG, "监控服务停止成功")

        } catch (e: Exception) {
            Log.e(TAG, "停止监控服务失败", e)
        }
    }

    /**
     * 测试启动指定应用
     */
    private fun launchTestApp() {
        val targetPackageName = "com.jingzhunxue.tifenben"

        try {
            val packageManager = packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackageName)

            if (launchIntent != null) {
                Log.d(TAG, "启动测试应用: $targetPackageName")
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)

                // 显示启动成功的提示
                Toast.makeText(this, "成功启动应用: $targetPackageName", Toast.LENGTH_SHORT).show()

            } else {
                Log.w(TAG, "无法获取 $targetPackageName 的启动Intent，应用可能不存在")
                Toast.makeText(this, "应用不存在或无法启动: $targetPackageName", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: $targetPackageName", e)
            Toast.makeText(this, "启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置服务状态（供外部调用，比如通过广播）
     */
    fun setServiceStatus(running: Boolean) {
        serviceRunning = running
        handler.post {
            updateServiceUI()
        }
    }

    /**
     * 权限检查结果的回调（可以在 onActivityResult 中处理）
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                // 权限申请结果
                handler.postDelayed({
                    checkPermissions()
                }, 500) // 延迟一点检查，确保权限状态已更新
            }
        }
    }
}