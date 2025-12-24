package com.haofenshu.lnkscreen

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SystemSettingsActivity : AppCompatActivity() {

    private lateinit var wifiStatusText: TextView
    private lateinit var brightnessButton: View
    private lateinit var soundSettingsButton: View
    private lateinit var dateTimeButton: View
    private lateinit var networkCheckButton: View
    private lateinit var shutdownButton: View
    private lateinit var rebootButton: View
    private lateinit var backButton: View
    private lateinit var versionText: TextView
    private lateinit var debugButtonsLayout: LinearLayout
    
    // 新增调试按钮
    private lateinit var developerButton: View
    private lateinit var applicationButton: View
    private lateinit var cameraButton: View
    private lateinit var galleryButton: View
    private lateinit var fileManagerButton: View

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var input = 0

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val REQUEST_STORAGE_PERMISSION = 1002
        private const val REQUEST_WRITE_PERMISSION = 1003
        const val KEY_INPUT = "key_input"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置状态栏颜色
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#FDEAC5")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.activity_system_settings1)

        initViews()
        initServices()
        setupClickListeners()
        updateNetworkStatus()
        input = intent.getIntExtra(KEY_INPUT, 0)
    }

    private fun initViews() {
        wifiStatusText = findViewById(R.id.wifiStatusText)
        brightnessButton = findViewById(R.id.brightnessButton)
        soundSettingsButton = findViewById(R.id.soundSettingsButton)
        dateTimeButton = findViewById(R.id.dateTimeButton)
        networkCheckButton = findViewById(R.id.networkCheckButton)
        shutdownButton = findViewById(R.id.shutdownButton)
        rebootButton = findViewById(R.id.rebootButton)
        backButton = findViewById(R.id.backButton)
        versionText = findViewById(R.id.versionText)
        debugButtonsLayout = findViewById(R.id.debugButtonsLayout)
        
        // 绑定调试按钮
        developerButton = findViewById(R.id.developerButton)
        applicationButton = findViewById(R.id.applicationButton)
        cameraButton = findViewById(R.id.cameraButton)
        galleryButton = findViewById(R.id.galleryButton)
        fileManagerButton = findViewById(R.id.fileManagerButton)

        // 设置版本号
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "版本号: ${packageInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "版本号: 未知"
        }

        // 检查是否为Debug模式
        checkDebugMode()
    }

    private fun initServices() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun setupClickListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            if (input == 0) {
                setResult(111111, Intent())
                finish()
            }
        }

        // 基本功能按钮
        brightnessButton.setOnClickListener { openBrightnessSettings() }
        soundSettingsButton.setOnClickListener { openSoundSettings() }
        dateTimeButton.setOnClickListener { openDateTimeSettings() }

        // 网络检测按钮
        networkCheckButton.setOnClickListener {
            performNetworkCheck()
        }

        // 电源管理按钮
        shutdownButton.setOnClickListener {
            showShutdownDialog()
        }
        rebootButton.setOnClickListener {
            showRebootDialog()
        }
        
        // 调试功能按钮点击事件
        developerButton.setOnClickListener {
            KioskUtils.openSystemSettings(this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        applicationButton.setOnClickListener {
            KioskUtils.openSystemSettings(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        }
        
        cameraButton.setOnClickListener {
            // 使用标准相机 Action
            tryLaunchIntent(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
        
        galleryButton.setOnClickListener {
            // 使用标准相册选择器
            tryLaunchIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
        }
        
        fileManagerButton.setOnClickListener {
            // 尝试使用标准文件管理器分类
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES)
            if (!tryLaunchIntent(intent)) {
                // 如果找不到通用的，尝试打开下载管理器（系统通常会跳转到文件浏览）
                tryLaunchIntent(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
            }
        }
    }

    /**
     * 尝试启动 Intent，如果失败则返回 false
     */
    private fun tryLaunchIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 检查系统是否有应用能处理这个 Intent
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkDebugMode() {
        val applicationInfo = applicationInfo
        val isDebug =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (isDebug) {
            // 显示Debug按钮组
            debugButtonsLayout.visibility = View.VISIBLE

            // 设置Debug按钮点击事件
            debugButtonsLayout.findViewById<View>(R.id.exitKioskButton).setOnClickListener {
                handleExitKioskMode()
            }
        }
    }

    private fun updateNetworkStatus() {
        val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
        val isConnected = networkInfo?.isConnected == true
        val isWifi = networkInfo?.type == ConnectivityManager.TYPE_WIFI

        when {
            isConnected && isWifi -> {
                wifiStatusText.text = "✓ WiFi已连接"
                wifiStatusText.setTextColor(Color.parseColor("#4CAF50"))
            }

            isConnected -> {
                wifiStatusText.text = "✓ 已连接到网络"
                wifiStatusText.setTextColor(Color.parseColor("#4CAF50"))
            }

            wifiManager.isWifiEnabled -> {
                wifiStatusText.text = "WiFi已开启，但未连接网络"
                wifiStatusText.setTextColor(Color.parseColor("#FF9800"))
            }

            else -> {
                wifiStatusText.text = "✗ 无网络连接"
                wifiStatusText.setTextColor(Color.parseColor("#F44336"))
            }
        }
    }

    private fun performNetworkCheck() {
        val networkInfo = KioskUtils.getNetworkStatusInfo(this)
        AlertDialog.Builder(this)
            .setTitle("网络检测")
            .setMessage("网络状态信息：\n\n$networkInfo")
            .setPositiveButton("打开WiFi设置") { _, _ ->
                openWifiSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openWifiSettings() {
        KioskUtils.openSystemSettings(this, Settings.ACTION_WIFI_SETTINGS)
    }

    private fun handleExitKioskMode() {
        AlertDialog.Builder(this)
            .setTitle("退出单应用模式")
            .setMessage("确定要退出单应用模式吗？这将清除所有Kiosk设置。")
            .setPositiveButton("确定") { _, _ ->
                val success = KioskUtils.disableKioskMode(this)
                if (success) {
                    showStatus("已退出单应用模式")
                    Handler(Looper.getMainLooper()).postDelayed({
                        finish()
                    }, 1000)
                } else {
                    showStatus("退出失败，请检查权限")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openBrightnessSettings() {
        // 自动清除亮度限制，兼容旧版本
        try {
            if (KioskUtils.isBrightnessRestricted(this)) {
                // 静默清除限制，不显示提示
                KioskUtils.clearBrightnessRestriction(this)
            }
        } catch (e: Exception) {
            // 忽略错误，继续打开设置
        }

        // 打开亮度设置页面
        KioskUtils.openSystemSettings(this, Settings.ACTION_DISPLAY_SETTINGS)
    }

    private fun openSoundSettings() {
        KioskUtils.openSystemSettings(this, Settings.ACTION_SOUND_SETTINGS)
    }

    private fun openDateTimeSettings() {
        KioskUtils.openSystemSettings(this, Settings.ACTION_DATE_SETTINGS)
    }

    private fun showStatus(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showShutdownDialog() {
        AlertDialog.Builder(this)
            .setTitle("关机")
            .setMessage("确定要关机吗？")
            .setPositiveButton("确定") { _, _ ->
                KioskUtils.shutdownDevice(this)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRebootDialog() {
        AlertDialog.Builder(this)
            .setTitle("重启")
            .setMessage("确定要重启吗？")
            .setPositiveButton("确定") { _, _ ->
                KioskUtils.rebootDevice(this)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}