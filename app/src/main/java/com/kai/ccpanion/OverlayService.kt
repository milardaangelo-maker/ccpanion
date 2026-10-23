// language: Kotlin, target: Android 7.0+ (API 24), file: OverlayService.kt
// One UI-stijl fake systeemupdate — template "settings" (download-pagina) of "installing" (zwarte installatie)
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

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (view != null) return START_NOT_STICKY
        if (!android.provider.Settings.canDrawOverlays(this)) return START_NOT_STICKY
        val template = i?.getStringExtra("template") ?: "installing"
        installing = template != "settings"

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = if (template == "settings") buildSettings() else buildInstalling()

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
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- template 1: One UI "Software-update beschikbaar" ----------
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
        val lpT = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpT.topMargin = dp(18)
        root.addView(title, lpT)

        val sub = TextView(this).apply {
            text = "Software-update beschikbaar"
            setTextColor(Color.rgb(165, 168, 173))
            textSize = 15f
            gravity = Gravity.CENTER
        }
        val lpS = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpS.topMargin = dp(6)
        root.addView(sub, lpS)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 21, 24))
        }
        val lpCard = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpCard.topMargin = dp(26)
        addRow(card, "One UI-versie", "8.0")
        addRow(card, "Android-versie", "16")
        addRow(card, "Grootte", "248,3 MB")
        addRow(card, "Beveiligingspatchniveau", "1 oktober 2026")
        root.addView(card, lpCard)

        val btn = TextView(this).apply {
            text = "Nu installeren"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundRect(Color.rgb(62, 128, 232), dp(28))
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val lpBtn = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(0)) }
        wrap.addView(btn, lpBtn)
        val lpWrap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpWrap.topMargin = dp(34)
        root.addView(wrap, lpWrap)
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

    // ---------- template 2: zwart Samsung-installatiescherm ----------
    private fun buildInstalling(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val logo = TextView(this).apply {
            text = "SAMSUNG"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.18f
        }
        val lpLogo = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpLogo.gravity = Gravity.CENTER_HORIZONTAL
        lpLogo.bottomMargin = dp(44)
        root.addView(logo, lpLogo)

        val lpRing = LinearLayout.LayoutParams(dp(190), dp(190))
        lpRing.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(SamsRing(this), lpRing)

        val txt = TextView(this).apply {
            text = "Software-update installeren…"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
        }
        val lpTxt = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpTxt.topMargin = dp(34)
        root.addView(txt, lpTxt)

        val sub = TextView(this).apply {
            text = "Schakel het apparaat niet uit."
            setTextColor(Color.rgb(165, 168, 173))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        val lpSub = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lpSub.topMargin = dp(8)
        root.addView(sub, lpSub)
        return root
    }

    private fun tick() {
        h.postDelayed(object : Runnable {
            override fun run() {
                val v = view ?: return
                pct = if (pct < 90) pct + (0..4).random() else (pct + 1).coerceAtMost(97)
                findRing(v)?.let { it.pct = pct; it.invalidate() }
                h.postDelayed(this, 700)
            }
        }, 700)
    }

    private fun findRing(v: View): SamsRing? {
        if (v is SamsRing) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findRing(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}

// blauw cirkel-icoon met dubbele updatepijlen
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

// samsung-achtige vooruitgangsring met percentage in het midden
class SamsRing(context: Context) : View(context) {

    var pct: Int = 0

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 40, 43)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val cx = s / 2f
        val cy = s / 2f
        val wSt = s * 0.055f
        track.strokeWidth = wSt
        arcP.strokeWidth = wSt
        val rad = s / 2f - s * 0.10f
        c.drawCircle(cx, cy, rad, track)
        val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawArc(rect, -90f, (pct / 100f) * 360f, false, arcP)
        text.textSize = s * 0.23f
        val baseline = cy - (text.ascent() + text.descent()) / 2f
        c.drawText("$pct%", cx, baseline, text)
    }
}
