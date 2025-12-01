package com.yunxiao.appmanager.floatwindow

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.yunxiao.appmanager.R

/**
 * 最小化悬浮窗 - 几乎不可见的小点
 * 用于维持服务在后台运行，资源占用极少
 */
class MiniFloatWindow(private val context: Context) : View(context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val params = createLayoutParams()
    private var isShowing = false

    // 记录触摸位置，用于拖拽
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        private const val TAG = "MiniFloatWindow"
        private const val FLOAT_SIZE = 300  // 1像素大小，几乎不可见
    }

    init {
        initView()
    }

    private fun initView() {
        // 设置背景为透明
        background = ColorDrawable(Color.TRANSPARENT)

        // 或者使用1x1像素的小圆点（如果需要调试）
         background = ContextCompat.getDrawable(context, R.drawable.mini_dot)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams().apply {
            width = FLOAT_SIZE
            height = FLOAT_SIZE

            // 设置窗口类型
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // 设置窗口标志
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

            // 设置位置
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100

            // 设置像素格式
            format = PixelFormat.TRANSLUCENT
        }

        return layoutParams
    }

    /**
     * 显示悬浮窗
     */
    fun show() {
        if (isShowing) return

        try {
            windowManager.addView(this, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        if (!isShowing) return

        try {
            windowManager.removeView(this)
            isShowing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 更新悬浮窗位置
     */
    fun updatePosition(x: Int, y: Int) {
        params.x = x
        params.y = y
        try {
            windowManager.updateViewLayout(this, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 检查是否正在显示
     */
    fun isShowing(): Boolean = isShowing

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                params.x = initialX + deltaX.toInt()
                params.y = initialY + deltaY.toInt()
                windowManager.updateViewLayout(this, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                // 可以在这里添加点击事件处理
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 释放资源
     */
    fun release() {
        hide()
    }
}