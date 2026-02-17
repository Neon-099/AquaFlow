    package com.aquaflow

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class AuthResult(
    val token: String,
    val userEmail: String,
    val userId: String?,
    val name: String?,
    val address: String?,
    val phone: String?
)

object AuthApi {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Android emulator -> localhost mapping. Replace with LAN IP for physical devices.
    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun login(email: String, password: String, callback: (Result<AuthResult>) -> Unit) {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/auth/signin")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        enqueue(request, email, callback)
    }

    fun signup(
        email: String,
        password: String,
        name: String,
        address: String,
        phone: String,
        callback: (Result<AuthResult>) -> Unit
    ) {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("name", name)
            .put("address", address)
            .put("phone", phone)
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/auth/signup")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        enqueue(request, email, callback)
    }

    fun getMe(token: String, callback: (Result<AuthResult>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/auth/me")
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Session expired")))
                        return
                    }

                    try {
                        val json = JSONObject(raw ?: "{}")
                        val user = json.optJSONObject("user")
                            ?: json.optJSONObject("data")?.optJSONObject("user")
                            ?: json.optJSONObject("data")

                        if (user == null) {
                            callback(Result.failure(Exception("Missing user in /auth/me response")))
                            return
                        }

                        val email = user.optString("email", "")
                        val id = user.optString("_id", user.optString("id", ""))
                        val name = if (user.has("name")) user.optString("name") else null
                        val address = if (user.has("address")) user.optString("address") else null
                        val phone = if (user.has("phone")) user.optString("phone") else null

                        callback(
                            Result.success(
                                AuthResult(
                                    token = token,
                                    userEmail = email,
                                    userId = id.ifBlank { null },
                                    name = name,
                                    address = address,
                                    phone = phone
                                )
                            )
                        )
                    } catch (e: JSONException) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun enqueue(request: Request, fallbackEmail: String, callback: (Result<AuthResult>) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Authentication failed")))
                        return
                    }

                    try {
                        val json = JSONObject(raw ?: "{}")
                        val token = json.optString("token", "")
                        if (token.isBlank()) {
                            callback(Result.failure(Exception("Missing auth token in response")))
                            return
                        }

                        val user = json.optJSONObject("user")
                        val email = user?.optString("email", fallbackEmail) ?: fallbackEmail
                        val id = user?.optString("id", null)
                        val name = if (user != null && user.has("name")) user.optString("name") else null
                        val address = if (user != null && user.has("address")) user.optString("address") else null
                        val phone = if (user != null && user.has("phone")) user.optString("phone") else null
                        callback(Result.success(AuthResult(token, email, id, name, address, phone)))
                    } catch (e: JSONException) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun extractMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            JSONObject(raw).optString("message", null)
        } catch (_: JSONException) {
            null
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
