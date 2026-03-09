package com.aquaflow.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object FcmApi {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun registerToken(authToken: String, fcmToken: String, callback: (Result<Unit>) -> Unit) {
        val body = JSONObject()
            .put("token", fcmToken)
            .put("platform", "android")
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/fcm/token")
            .header("Authorization", "Bearer $authToken")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "FCM token registration failed")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    fun unregisterToken(authToken: String, fcmToken: String, callback: (Result<Unit>) -> Unit) {
        val body = JSONObject().put("token", fcmToken).toString()
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/fcm/token")
            .header("Authorization", "Bearer $authToken")
            .delete(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "FCM token unregister failed")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    private fun extractMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val json = JSONObject(raw)
            json.optString("message", json.optString("error", null))
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}

object PushRegistration {
    private const val PREFS = "auth"
    private const val KEY_AUTH_TOKEN = "token"
    private const val KEY_FCM_TOKEN = "fcmToken"
    private const val TAG = "PushRegistration"
    private const val REQ_POST_NOTIF = 1101

    fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_POST_NOTIF
            )
        }
    }

    fun syncTokenIfPossible(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val authToken = prefs.getString(KEY_AUTH_TOKEN, null)
        if (authToken.isNullOrBlank()) return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Failed to fetch FCM token", task.exception)
                return@addOnCompleteListener
            }

            val freshToken = task.result?.trim().orEmpty()
            if (freshToken.isBlank()) return@addOnCompleteListener

            val savedToken = prefs.getString(KEY_FCM_TOKEN, null)
            if (freshToken == savedToken) return@addOnCompleteListener

            FcmApi.registerToken(authToken, freshToken) { result ->
                result.onSuccess {
                    prefs.edit().putString(KEY_FCM_TOKEN, freshToken).apply()
                    Log.d(TAG, "FCM token synced")
                }.onFailure {
                    Log.w(TAG, "FCM token sync failed: ${it.message}")
                }
            }
        }
    }

    fun onNewFcmToken(context: Context, newToken: String) {
        if (newToken.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, newToken).apply()

        val authToken = prefs.getString(KEY_AUTH_TOKEN, null)
        if (authToken.isNullOrBlank()) return

        FcmApi.registerToken(authToken, newToken) {
            it.onFailure { e -> Log.w(TAG, "onNewToken register failed: ${e.message}") }
        }
    }

    fun unregisterCurrentToken(context: Context, onDone: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val authToken = prefs.getString(KEY_AUTH_TOKEN, null)
        val fcmToken = prefs.getString(KEY_FCM_TOKEN, null)

        if (authToken.isNullOrBlank() || fcmToken.isNullOrBlank()) {
            prefs.edit().remove(KEY_FCM_TOKEN).apply()
            onDone()
            return
        }

        FcmApi.unregisterToken(authToken, fcmToken) {
            prefs.edit().remove(KEY_FCM_TOKEN).apply()
            onDone()
        }
    }
}
