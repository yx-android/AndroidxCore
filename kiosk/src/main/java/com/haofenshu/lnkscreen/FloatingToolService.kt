package com.haofenshu.lnkscreen

import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class FloatingToolService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_tools, null)

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingView, params)

        setupClickListeners()
        setupDragListener(params)
    }

    private fun setupClickListeners() {
        floatingView?.findViewById<View>(R.id.btn_close_floating)?.setOnClickListener {
            stopSelf()
        }

        floatingView?.findViewById<Button>(R.id.btn_launch_shizuku_setup)?.setOnClickListener {
            try {
                val intent = Intent().apply {
                    setClassName(packageName, "com.haofenshu.tfb.adbcmd.ShizukuSetupActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法启动本地 Shizuku_Setup 测试页\n${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        floatingView?.findViewById<Button>(R.id.btn_launch_shizuku)?.setOnClickListener {
            launchApp(
                "moe.shizuku.privileged.api",
                "https://appvm.yunxiao.com/dl/69abf3189476706d41f8370f"
            )
        }

        floatingView?.findViewById<Button>(R.id.btn_launch_icebox)?.setOnClickListener {
            launchApp(
                "com.catchingnow.icebox",
                "https://hyll-oss.yunxiao.com/packages/mobile_app/product/users/admin/2026-03-08/icebox_coolapk_3.25.3285237303429-20219-o_1h1tvn5pjci61j2d1du91gcr1nn113-uid-184454.apk"
            )
        }

        floatingView?.findViewById<Button>(R.id.btn_launch_app_rec)?.setOnClickListener {
            launchApp(
                "com.michael.apprec",
                "https://hyll-oss.yunxiao.com/packages/mobile_app/product/users//2026-03-09/App%20Rec-1-1.6.0.apk"
            )
        }

        floatingView?.findViewById<Button>(R.id.btn_exit_kiosk)?.setOnClickListener {
            handleExitKioskMode()
        }

        floatingView?.findViewById<Button>(R.id.btn_remove_admin)?.setOnClickListener {
            handleRemoveAdmin()
        }
    }
    
    private fun handleRemoveAdmin() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("撤销设备管理者")
            .setMessage("确定要撤销当前应用的“设备管理者”权限吗？\n撤销后会导致免 root 冻结、禁用等高级功能无法使用，该悬浮窗也会随之关闭。")
            .setPositiveButton("确定") { _, _ ->
                val success = KioskUtils.removeActiveAdmin(this)
                if (success) {
                    Toast.makeText(this, "已撤销设备管理者权限", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopSelf()
                    }, 1000)
                } else {
                    Toast.makeText(this, "撤销失败或当前未激活该权限", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        dialog.show()
    }

    private fun handleExitKioskMode() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("退出单应用模式")
            .setMessage("确定要退出单应用模式吗？这将清除所有Kiosk设置。")
            .setPositiveButton("确定") { _, _ ->
                val success = KioskUtils.disableKioskMode(this)
                if (success) {
                    Toast.makeText(this, "已退出单应用模式", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopSelf()
                    }, 1000)
                } else {
                    Toast.makeText(this, "退出失败，请检查权限", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }
        
        dialog.show()
    }
    
    private fun launchApp(packageName: String, downloadUrl: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "未找到应用，正在跳转浏览器下载...", Toast.LENGTH_SHORT).show()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动应用或浏览器: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDragListener(params: WindowManager.LayoutParams) {
        val dragHandle = floatingView?.findViewById<View>(R.id.ll_drag_handle)
        dragHandle?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
    }
}
