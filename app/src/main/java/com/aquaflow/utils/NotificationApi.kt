package com.aquaflow.utils

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class NotificationRow(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String?,
    val status: String?,
    val orderId: String?
)

object NotificationApi {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun listOrderNotifications(token: String, callback: (Result<List<NotificationRow>>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/orders?unread=false&limit=50")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch notifications failed")))
                        return
                    }
                    try {
                        val rows = JSONObject(raw).optJSONArray("data") ?: JSONArray()
                        callback(Result.success(parseRows(rows)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun listMessageNotifications(token: String, callback: (Result<List<NotificationRow>>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/messages?unread=false&limit=50")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch notifications failed")))
                        return
                    }
                    try {
                        val rows = JSONObject(raw).optJSONArray("data") ?: JSONArray()
                        callback(Result.success(parseRows(rows)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun markOrderRead(token: String, ids: List<String>, callback: (Result<Unit>) -> Unit) {
        markRead(token, "/api/v1/notifications/orders/mark-read", ids, callback)
    }

    fun markMessageRead(token: String, ids: List<String>, callback: (Result<Unit>) -> Unit) {
        markRead(token, "/api/v1/notifications/messages/mark-read", ids, callback)
    }

    fun getUnreadMessageCount(token: String, callback: (Result<Int>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/messages/unread-count")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch unread count failed")))
                        return
                    }
                    try {
                        val count = JSONObject(raw).optJSONObject("data")?.optInt("count", 0) ?: 0
                        callback(Result.success(count))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun getUnreadOrderCount(token: String, callback: (Result<Int>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/notifications/orders/unread-count")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch unread count failed")))
                        return
                    }
                    try {
                        val count = JSONObject(raw).optJSONObject("data")?.optInt("count", 0) ?: 0
                        callback(Result.success(count))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun markRead(token: String, path: String, ids: List<String>, callback: (Result<Unit>) -> Unit) {
        val body = JSONObject().put("ids", JSONArray(ids)).toString()
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", "Bearer $token")
            .put(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Update failed")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    private fun parseRows(rows: JSONArray): List<NotificationRow> {
        val out = mutableListOf<NotificationRow>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            out += NotificationRow(
                id = row.optString("_id"),
                type = row.optString("type", "order_status"),
                title = row.optString("title", "Notification"),
                message = row.optString("message", ""),
                isRead = row.optBoolean("is_read", false),
                createdAt = row.optString("created_at", null),
                status = row.optString("status", null),
                orderId = row.optString("order_id", null)
            )
        }
        return out
    }

    private fun extractMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try { JSONObject(raw).optString("message", null) } catch (_: Exception) { null }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
