package com.llmcompanion.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class AvatarService : Service() {

    private lateinit var windowManager: WindowManager
    private var avatarView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val imageView = ImageView(this)
        imageView.setImageResource(android.R.drawable.ic_menu_help)
        imageView.setBackgroundResource(android.R.drawable.screen_background_light_transparent)

        avatarView = imageView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(avatarView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (avatarView != null) {
            windowManager.removeView(avatarView)
        }
    }
}