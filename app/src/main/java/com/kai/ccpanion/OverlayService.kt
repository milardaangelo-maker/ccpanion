// language: Kotlin, target: Android 7.0+ (API 24), file: OverlayService.kt
// One UI-fake-updatescherm — template "installing" = na-update optimaliseren; "settings" = update-beschikbaar-pagina
package com.kai.ccpanion

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private var view: View? = null
    private val h = Handler(Looper.getMainLooper())
    private var installing = false
    private var pct = 0
    private var bar: ThinBar? = null
    private var pctText: TextView? = null
    private var statusText: TextView? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (view != null) return START_NOT_STICKY
        if (!android.provider.Settings.canDrawOverlays(this)) return START_NOT_STICKY
        val template = i?.getStringExtra("template") ?: "installing"
        installing = template != "settings"

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = if (template == "settings") buildSettings() else buildOneUI()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(root, params)
        view = root
        if (installing) tick()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        view?.let { v -> (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        view = null
        bar = null; pctText = null; statusText = null
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- template 1: echt One UI na-update scherm ----------
    private fun buildOneUI(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val logo = TextView(this).apply {
            text = "One UI"
            setTextColor(Color.WHITE)
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.01f
            gravity = Gravity.CENTER
        }
        root.addView(logo, col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0))

        val star = TextView(this).apply {
            text = "✦"
            setTextColor(Color.rgb(190, 194, 200))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        root.addView(star, col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(16)))

        val barW = (resources.displayMetrics.widthPixels * 0.62).toInt()
        bar = ThinBar(this)
        root.addView(bar, col(barW, dp(4), dp(28)))

        pctText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            text = "0%"
        }
        root.addView(pctText, col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(20)))

        statusText = TextView(this).apply {
            setTextColor(Color.rgb(224, 226, 229))
            textSize = 14f
            gravity = Gravity.CENTER
            text = "Apps optimaliseren…"
        }
        root.addView(statusText, col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(8)))
        return root
    }

    private fun col(w: Int, h: Int, top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(w, if (h == 0) LinearLayout.LayoutParams.WRAP_CONTENT else h)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = top
        return lp
    }

    // ---------- template 2: One UI "Software-update beschikbaar" ----------
    private fun buildSettings(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(70), dp(28), dp(28))
        }

        root.addView(UpdateIcon(this), LinearLayout.LayoutParams(dp(96), dp(96)))

        val title = TextView(this).apply {
            text = "Software-update"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val lpT = col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(18))
        root.addView(title, lpT)

        val sub = TextView(this).apply {
            text = "Software-update beschikbaar"
            setTextColor(Color.rgb(165, 168, 173))
            textSize = 15f
            gravity = Gravity.CENTER
        }
        root.addView(sub, col(LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(6)))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 21, 24))
        }
        addRow(card, "One UI-versie", "8.0")
        addRow(card, "Android-versie", "16")
        addRow(card, "Grootte", "248,3 MB")
        addRow(card, "Beveiligingspatchniveau", "1 oktober 2026")
        root.addView(card, col(LinearLayout.LayoutParams.MATCH_PARENT, 0, dp(26)))

        val btn = TextView(this).apply {
            text = "Nu installeren"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundRect(Color.rgb(62, 128, 232), dp(28))
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        root.addView(btn, col(LinearLayout.LayoutParams.MATCH_PARENT, 0, dp(34)))
        return root
    }

    private fun addRow(card: LinearLayout, k: String, v: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(20), dp(14), dp(20), dp(14)) }
        val a = TextView(this).apply { text = k; setTextColor(Color.WHITE); textSize = 15f }
        val b = TextView(this).apply { text = v; setTextColor(Color.rgb(165, 168, 173)); textSize = 15f; gravity = Gravity.END }
        row.addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(row)
        val sep = View(this)
        sep.setBackgroundColor(Color.rgb(34, 35, 38))
        card.addView(sep, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun roundRect(color: Int, radius: Int): android.graphics.drawable.GradientDrawable {
        val d = android.graphics.drawable.GradientDrawable()
        d.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        d.setColor(color)
        d.cornerRadius = radius.toFloat()
        return d
    }

    // ---------- ticker ----------
    private fun tick() {
        h.postDelayed(object : Runnable {
            override fun run() {
                if (view == null) return
                pct = when {
                    pct < 55 -> pct + (2..6).random()
                    pct < 90 -> pct + (1..3).random()
                    else -> (pct + 1).coerceAtMost(100)
                }
                bar?.pct = pct
                bar?.invalidate()
                pctText?.text = "$pct%"
                statusText?.text = if (pct < 92) "Apps optimaliseren…" else if (pct < 100) "Bijna klaar…" else "Wordt opnieuw gestart…"
                if (pct < 100) h.postDelayed(this, 700)
            }
        }, 700)
    }
}

// cirkel-icoon met dubbele updatepijlen (settings-template)
class UpdateIcon(context: Context) : View(context) {

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(62, 128, 232)
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val cx = s / 2f
        val cy = s / 2f
        c.drawCircle(cx, cy, s / 2f, circle)

        val r = s * 0.27f
        stroke.strokeWidth = s * 0.055f
        c.drawArc(cx - r, cy - r, cx + r, cy + r, -60f, 120f, false, stroke)
        c.drawArc(cx - r, cy - r, cx + r, cy + r, 120f, 120f, false, stroke)
        head(c, cx, cy, r, 60f, s)
        head(c, cx, cy, r, 240f, s)
    }

    private fun head(c: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float, s: Float) {
        val a = Math.toRadians(angleDeg.toDouble())
        val x = cx + (r * Math.cos(a)).toFloat()
        val y = cy + (r * Math.sin(a)).toFloat()
        val t = Math.toRadians((angleDeg + 90).toDouble())
        val dx = Math.cos(t).toFloat()
        val dy = Math.sin(t).toFloat()
        val nx = Math.cos(t + Math.PI / 2).toFloat()
        val ny = Math.sin(t + Math.PI / 2).toFloat()
        val hl = s * 0.11f
        val hw = s * 0.06f
        val p = Path()
        p.moveTo(x + dx * hl, y + dy * hl)
        p.lineTo(x + nx * hw, y + ny * hw)
        p.lineTo(x - nx * hw, y - ny * hw)
        p.close()
        c.drawPath(p, fill)
    }
}

// dunne One UI voortgangsbalk (lichte track, witte fill, ronde uiteinden)
class ThinBar(context: Context) : View(context) {

    var pct: Int = 0

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(74, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val hh = height.toFloat()
        val r = hh / 2f
        c.drawRoundRect(RectF(0f, 0f, w, hh), r, r, track)
        if (pct > 0) {
            val fw = (w * pct / 100f).coerceAtLeast(hh)
            c.drawRoundRect(RectF(0f, 0f, fw, hh), r, r, fillP)
        }
    }
}
