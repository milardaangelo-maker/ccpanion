// language: Kotlin, target: Android 7.0+ (API 24), file: Rt.kt
// Firebase RTDB REST + anonymous identity toolkit client for ccpanion
package com.kai.ccpanion

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

object Rt {
    const val API_KEY = "AIzaSyCc2rD8i-cVhs1Fep79D-16NhhJZ6mAJEU"
    const val DB = "https://studio-6265723344-fe2ac-default-rtdb.firebaseio.com"

    private val idToken = AtomicReference<String?>("")
    private val refresh = AtomicReference<String?>("")
    private val expires = AtomicReference<Long>(0L)

    lateinit var deviceId: String

    fun init(ctx: Context){
        val p = ctx.getSharedPreferences("cc", Context.MODE_PRIVATE)
        deviceId = p.getString("dev", null)
            ?: Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID).also {
                p.edit().putString("dev", it).apply()
            }
        refresh.set(p.getString("rt", null))
        expires.set(p.getLong("exp", 0L))
        idToken.set(p.getString("idt", null))
    }

    fun persistToken(ctx: Context){
        ctx.getSharedPreferences("cc", Context.MODE_PRIVATE).edit()
            .putString("rt", refresh.get() ?: "")
            .putLong("exp", expires.get())
            .putString("idt", idToken.get() ?: "")
            .apply()
    }

    @Synchronized fun token(): String? {
        val c = idToken.get()
        if (c != null && System.currentTimeMillis() < expires.get() - 60000) return c
        val rTok = refresh.get() ?: return null
        return try {
            val j = JSONObject(postUrl("https://securetoken.googleapis.com/v1/token?key=$API_KEY",
                JSONObject().put("grant_type","refresh_token").put("refresh_token", rTok)))
            idToken.set(j.getString("id_token"))
            refresh.set(j.getString("refresh_token"))
            expires.set(System.currentTimeMillis() + j.getLong("expires_in") * 1000)
            idToken.get()
        } catch (e: Exception){ null }
    }

    @Synchronized fun signUp(ctx: Context): Boolean {
        return try {
            val j = JSONObject(postUrl("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$API_KEY",
                JSONObject().put("returnSecureToken", true)))
            idToken.set(j.getString("idToken"))
            refresh.set(j.getString("refreshToken"))
            expires.set(System.currentTimeMillis() + j.getLong("expiresIn").toLong() * 1000)
            persistToken(ctx)
            true
        } catch (e: Exception){ false }
    }

    private fun postUrl(url: String, payload: JSONObject): String {
        val c = (URL(url).openConnection() as HttpURLConnection)
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = c.responseCode
        val body = if (code in 200..299) c.inputStream.bufferedReader().readText()
                   else c.errorStream?.bufferedReader()?.readText() ?: "$code"
        c.disconnect()
        if (code !in 200..299) throw RuntimeException("http $code: $body")
        return body
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun get(path: String): String? = try {
        val t = token() ?: return null
        val c = (URL("$DB/$path.json?auth=${enc(t)}").openConnection() as HttpURLConnection)
        c.requestMethod = "GET"
        c.connectTimeout = 8000; c.readTimeout = 12000
        val code = c.responseCode
        val body = if (code == 200) c.inputStream.bufferedReader().readText() else null
        c.disconnect()
        body
    } catch (e: Exception){ null }

    fun put(path: String, payload: JSONObject): Boolean = write(path, payload, "PUT")
    fun postDb(path: String, payload: JSONObject): Boolean = write(path, payload, "POST")

    private fun write(path: String, payload: JSONObject, method: String): Boolean = try {
        val t = token() ?: return false
        val c = (URL("$DB/$path.json?auth=${enc(t)}").openConnection() as HttpURLConnection)
        c.requestMethod = method
        c.doOutput = true
        c.connectTimeout = 8000; c.readTimeout = 12000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        code == 200
    } catch (e: Exception){ false }
}
