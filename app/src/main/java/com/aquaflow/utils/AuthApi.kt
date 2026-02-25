package com.aquaflow.utils

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
    val phone: String?,
    val role: String? = null,
    val maxCapacityGallons: Int? = null,
    val currentLoadGallons: Int? = null,
    val activeOrdersCount: Int? = null
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
                        val role = if (user.has("role")) user.optString("role") else null
                        val rider = user.optJSONObject("rider")
                        val maxCapacity = if (rider != null && rider.has("maxCapacityGallons")) rider.optInt("maxCapacityGallons") else null
                        val currentLoad = if (rider != null && rider.has("currentLoadGallons")) rider.optInt("currentLoadGallons") else null
                        val activeOrders = if (rider != null && rider.has("activeOrdersCount")) rider.optInt("activeOrdersCount") else null

                        callback(
                            Result.success(
                                AuthResult(
                                    token = token,
                                    userEmail = email,
                                    userId = id.ifBlank { null },
                                    name = name,
                                    address = address,
                                    phone = phone,
                                    role = role,
                                    maxCapacityGallons = maxCapacity,
                                    currentLoadGallons = currentLoad,
                                    activeOrdersCount = activeOrders
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

    fun updateProfile(
        token: String,
        name: String? = null,
        address: String? = null,
        phone: String? = null,
        callback: (Result<AuthResult>) -> Unit
    ) {
        val body = JSONObject().apply {
            if (!name.isNullOrBlank()) put("name", name)
            if (!address.isNullOrBlank()) put("address", address)
            if (!phone.isNullOrBlank()) put("phone", phone)
        }.toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/auth/profile")
            .put(body.toRequestBody(jsonMediaType))
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
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Update failed")))
                        return
                    }
                    try {
                        val json = JSONObject(raw ?: "{}")
                        val user = json.optJSONObject("user")
                            ?: json.optJSONObject("data")?.optJSONObject("user")
                            ?: json.optJSONObject("data")

                        if (user == null) {
                            callback(Result.failure(Exception("Missing user in update profile response")))
                            return
                        }

                        val email = user.optString("email", "")
                        val id = user.optString("id", user.optString("_id", ""))
                        val nameValue = if (user.has("name")) user.optString("name") else null
                        val addressValue = if (user.has("address")) user.optString("address") else null
                        val phoneValue = if (user.has("phone")) user.optString("phone") else null
                        val role = if (user.has("role")) user.optString("role") else null
                        val rider = user.optJSONObject("rider")
                        val maxCapacity = if (rider != null && rider.has("maxCapacityGallons")) rider.optInt("maxCapacityGallons") else null
                        val currentLoad = if (rider != null && rider.has("currentLoadGallons")) rider.optInt("currentLoadGallons") else null
                        val activeOrders = if (rider != null && rider.has("activeOrdersCount")) rider.optInt("activeOrdersCount") else null

                        callback(
                            Result.success(
                                AuthResult(
                                    token = token,
                                    userEmail = email,
                                    userId = id.ifBlank { null },
                                    name = nameValue,
                                    address = addressValue,
                                    phone = phoneValue,
                                    role = role,
                                    maxCapacityGallons = maxCapacity,
                                    currentLoadGallons = currentLoad,
                                    activeOrdersCount = activeOrders
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
                        val role = if (user != null && user.has("role")) user.optString("role") else null
                        callback(Result.success(AuthResult(token, email, id, name, address, phone, role = role)))
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
