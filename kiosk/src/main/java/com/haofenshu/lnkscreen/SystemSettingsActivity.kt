package com.haofenshu.lnkscreen

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private lateinit var appManagementButton: View
    private lateinit var cameraButton: View
    private lateinit var galleryButton: View
    private lateinit var fileManagerButton: View

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private var input = 1

    // 监听网络变化的广播接收器
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNetworkStatus()
        }
    }

    companion object {
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
        
        input = intent.getIntExtra(KEY_INPUT, 0)
        if (input == 0) {
            setResult(111111, Intent())
        }
    }

    override fun onStart() {
        super.onStart()
        // 1. 每次页面回到前台时，主动刷新一次网络状态
        updateNetworkStatus()
        // 2. 注册广播监听实时变化
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // 页面不可见时注销监听，避免内存泄漏
        try {
            unregisterReceiver(networkReceiver)
        } catch (e: Exception) {}
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

        developerButton = findViewById(R.id.developerButton)
        applicationButton = findViewById(R.id.applicationButton)
        appManagementButton = findViewById(R.id.appManagementButton)
        cameraButton = findViewById(R.id.cameraButton)
        galleryButton = findViewById(R.id.galleryButton)
        fileManagerButton = findViewById(R.id.fileManagerButton)

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "版本号: ${packageInfo.versionName}"
        } catch (e: Exception) {
            versionText.text = "版本号: 未知"
        }

        checkDebugMode()
    }

    private fun initServices() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        brightnessButton.setOnClickListener { openBrightnessSettings() }
        soundSettingsButton.setOnClickListener { openSoundSettings() }
        dateTimeButton.setOnClickListener { openDateTimeSettings() }

        networkCheckButton.setOnClickListener {
            performNetworkCheck()
        }

        shutdownButton.setOnClickListener {
            showShutdownDialog()
        }
        rebootButton.setOnClickListener {
            showRebootDialog()
        }

        developerButton.setOnClickListener {
            KioskUtils.openSystemSettings(this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        applicationButton.setOnClickListener {
            KioskUtils.openSystemSettings(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        }
        
        appManagementButton.setOnClickListener {
            startActivity(Intent(this, AppManagementActivity::class.java))
        }

        cameraButton.setOnClickListener {
            tryLaunchIntent(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }

        galleryButton.setOnClickListener {
            tryLaunchIntent(
                Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_GALLERY
                )
            )
        }

        fileManagerButton.setOnClickListener {
            val intent =
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES)
            if (!tryLaunchIntent(intent)) {
                tryLaunchIntent(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
            }
        }
    }

    private fun tryLaunchIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebug) {
            debugButtonsLayout.visibility = View.VISIBLE
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
        try {
            if (KioskUtils.isBrightnessRestricted(this)) {
                KioskUtils.clearBrightnessRestriction(this)
            }
        } catch (e: Exception) {}
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
