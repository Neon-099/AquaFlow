package com.aquaflow.utils

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object ChatSocket {
    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun connect(token: String): Socket {
        val options = IO.Options().apply {
            query = "token=$token"
            transports = arrayOf("websocket", "polling")
            extraHeaders = mapOf("Authorization" to listOf("Bearer $token"))
        }
        return IO.socket(BASE_URL, options).connect()
    }

    fun emitWithAck(socket: Socket, event: String, payload: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {;
        socket.emit(event, payload, Ack { args ->
            val ack = args.firstOrNull() as? JSONObject
            if (ack == null) {
                onError("$event failed")
                return@Ack
            }
            val ok = ack.optBoolean("ok", false)
            if (ok) {
                val data = ack.optJSONObject("data") ?: ack
                onSuccess(data)
            } else {
                onError(ack.optString("error", "$event failed"))
            }
        })
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
