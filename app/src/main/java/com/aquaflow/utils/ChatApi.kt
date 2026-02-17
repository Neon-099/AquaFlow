package com.aquaflow.utils

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ConversationRow(
    val id: String,
    val counterpartyName: String,
    val counterpartyRole: String?,
    val counterpartyLabel: String?,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val orderId: String?,
    val orderStatus: String?,
    val unreadCount: Int,
    val archivedAt: String?
)

data class ConversationMessage(
    val id: String,
    val message: String,
    val senderId: String,
    val receiverId: String?,
    val timestamp: String?,
    val seenAt: String?
)

object ChatApi {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun listConversations(
        token: String,
        includeArchived: Boolean = false,
        callback: (Result<List<ConversationRow>>) -> Unit
    ) {
        val url = "$BASE_URL/api/v1/chat/conversations?archived=${if (includeArchived) "true" else "false"}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Failed to load conversations")))
                        return
                    }
                    try {
                        val arr = JSONObject(raw).getJSONArray("data")
                        val rows = mutableListOf<ConversationRow>()
                        for (i in 0 until arr.length()) rows += parseConversation(arr.getJSONObject(i))
                        callback(Result.success(rows))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun getMessages(
        token: String,
        conversationId: String,
        callback: (Result<List<ConversationMessage>>) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/chat/conversations/$conversationId/messages")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Failed to load messages")))
                        return
                    }
                    try {
                        val data = JSONObject(raw).getJSONObject("data")
                        val messages = data.getJSONArray("messages")
                        val out = mutableListOf<ConversationMessage>()
                        for (i in 0 until messages.length()) out += parseMessage(messages.getJSONObject(i))
                        callback(Result.success(out))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun sendMessage(
        token: String,
        conversationId: String,
        message: String,
        callback: (Result<ConversationMessage>) -> Unit
    ) {
        val body = JSONObject().put("message", message).toString()
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/chat/conversations/$conversationId/messages")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Failed to send message")))
                        return
                    }
                    try {
                        val saved = JSONObject(raw).getJSONObject("data")
                        callback(Result.success(parseMessage(saved)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun markSeen(token: String, conversationId: String, callback: (Result<Unit>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/chat/conversations/$conversationId/seen")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Failed to mark seen")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    private fun parseConversation(json: JSONObject): ConversationRow {
        val participants = json.optJSONArray("participants") ?: JSONArray()
        var otherName = "Chat"
        var otherRole: String? = null

        for (i in 0 until participants.length()) {
            val p = participants.getJSONObject(i)
            val role = p.optString("role")
            val name = p.optString("name")
            if (!name.isNullOrBlank()) {
                otherName = name
                otherRole = role
                break
            }
        }

        return ConversationRow(
            id = json.optString("_id"),
            counterpartyName = otherName,
            counterpartyRole = json.optString("counterpartyRole", otherRole),
            counterpartyLabel = json.optString("counterpartyLabel", null),
            lastMessage = json.optString("lastMessage", null),
            lastMessageAt = json.optString("lastMessageAt", null),
            orderId = json.optString("orderId", null),
            orderStatus = json.optString("orderStatus", null),
            unreadCount = json.optInt("unreadCount", 0),
            archivedAt = json.optString("archivedAt", null)
        )
    }

    private fun parseMessage(json: JSONObject): ConversationMessage {
        return ConversationMessage(
            id = json.optString("_id"),
            message = json.optString("message"),
            senderId = json.optString("senderId"),
            receiverId = json.optString("receiverId", null),
            timestamp = json.optString("timestamp", null),
            seenAt = json.optString("seenAt", null)
        )
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
