package com.aquaflow.utils

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class MobileOrder (
    val id : String,
    val orderCode: String?,
    val status: String,
    val paymentStatus: String,
    val quantity: Int,
    val gallonType: String,
    val totalAmount: Double,
    val paymentMethod: String,
    val etaText: String?,
    val createdAt: String?,
    val customerName: String? = null,
    val customerAddress: String? = null,
    val assignedRiderId: String? = null,
    val assignedToMe: Boolean = false
);

data class MobileOrderDetails(
    val order: MobileOrder,
    val riderName: String?,
    val riderPhone: String?,
    val paymentProviderStatus: String?
)

data class CreateOrderPayload(
    val waterQuantity: Int,
    val gallonType: String,      // "SLIM" or "ROUND"
    val totalAmount: Double,
    val paymentMethod: String,   // "COD" or "GCASH"
    val gcashPaymentIntentId: String? = null
)

data class GcashPreparation(
    val paymentIntentId: String,
    val checkoutUrl: String
)

object OrderApi {
    private val client = OkHttpClient()  //CALL API
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private const val RAW_BASE_URL = "aqua-flows.onrender.com"
    private val BASE_URL = normalizeBaseUrl(RAW_BASE_URL)

    fun createOrder(
        token: String,
        payload: CreateOrderPayload,
        callback: (Result<Pair<MobileOrder, String?>>) -> Unit
    ) {
        //PAYLOAD STRUCTURE (expected request to server)
        val body = JSONObject()
            .put("water_quantity", payload.waterQuantity)
            .put("gallon_type", payload.gallonType)
            .put("total_amount", payload.totalAmount)
            .put("payment_method", payload.paymentMethod)
            .apply {
                if (!payload.gcashPaymentIntentId.isNullOrBlank()) {
                    put("gcash_payment_intent_id", payload.gcashPaymentIntentId)
                }
            }
            .toString()

        //API REQUEST
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/orders")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Create order failed")))
                        return
                    }
                    try {
                        val root = JSONObject(raw)
                        val data = root.getJSONObject("data")
                        val orderJson = data.getJSONObject("order")
                        val paymentUrl = data.optJSONObject("payment")
                            ?.optString("checkout_url", null)

                        callback(Result.success(parseOrder(orderJson) to paymentUrl))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun prepareGcashPayment(
        token: String,
        payload: CreateOrderPayload,
        callback: (Result<GcashPreparation>) -> Unit
    ) {
        val body = JSONObject()
            .put("water_quantity", payload.waterQuantity)
            .put("gallon_type", payload.gallonType)
            .put("total_amount", payload.totalAmount)
            .put("payment_method", "GCASH")
            .toString()

        val request = Request.Builder()
            .url("$BASE_URL/api/v1/orders/gcash_prepare")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "GCASH preparation failed")))
                        return
                    }
                    try {
                        val data = JSONObject(raw).getJSONObject("data")
                        val paymentIntentId = data.optString("payment_intent_id", "")
                        val checkoutUrl = data.optString("checkout_url", "")
                        if (paymentIntentId.isBlank() || checkoutUrl.isBlank()) {
                            callback(Result.failure(Exception("Missing checkout URL from payment provider")))
                            return
                        }
                        callback(Result.success(GcashPreparation(paymentIntentId, checkoutUrl)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun listMyOrders(token: String, callback: (Result<List<MobileOrder>>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/orders")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch orders failed")))
                        return
                    }
                    try {
                        val dataArray = JSONObject(raw).getJSONArray("data")
                        val parsed = mutableListOf<MobileOrder>()
                        for (i in 0 until dataArray.length()) {
                            parsed += parseOrder(dataArray.getJSONObject(i))
                        }
                        callback(Result.success(parsed))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun getOrderById(token: String, orderId: String, callback: (Result<MobileOrderDetails>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/orders/$orderId")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Fetch order failed")))
                        return
                    }
                    try {
                        val data = JSONObject(raw).getJSONObject("data")
                        val order = parseOrder(data.getJSONObject("order"))
                        val riderName = data.optJSONObject("rider")?.optString("name", null)
                        val riderPhone = data.optJSONObject("rider")?.optString("phone", null)
                        val paymentStatus = data.optJSONObject("payment")?.optString("status", null)
                        callback(Result.success(MobileOrderDetails(order, riderName, riderPhone, paymentStatus)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun cancelOrder(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/api/v1/orders/$orderId/cancel")
            .header("Authorization", "Bearer $token")
            .put("{}".toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Cancel failed")))
                        return
                    }
                    try {
                        val orderJson = JSONObject(raw).getJSONObject("data")
                        callback(Result.success(parseOrder(orderJson)))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    fun confirmOrder(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/confirm", callback)
    }

    fun confirmPickup(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/pickup", callback)
    }

    fun startDelivery(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/start_delivery", callback)
    }

    fun markDelivered(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/mark_delivered", callback)
    }

    fun confirmPayment(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/confirm_payment", callback)
    }

    fun cancelPickup(token: String, orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        updateOrderStatus(token, "/api/v1/orders/$orderId/cancel_pickup", callback)
    }

    fun bulkConfirmPickup(
        token: String,
        orderIds: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        updateOrderStatusBulk(token, "/api/v1/orders/bulk/pickup", orderIds, callback)
    }

    fun bulkStartDelivery(
        token: String,
        orderIds: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        updateOrderStatusBulk(token, "/api/v1/orders/bulk/start_delivery", orderIds, callback)
    }

    private fun parseOrder(json: JSONObject): MobileOrder {
        return MobileOrder(
            id = json.optString("_id"),
            orderCode = optNormalized(json, "order_code"),
            status = optNormalized(json, "status").orEmpty(),
            paymentStatus = optNormalized(json, "payment_status").orEmpty(),
            quantity = json.optInt("water_quantity", 0),
            gallonType = optNormalized(json, "gallon_type").orEmpty(),
            totalAmount = json.optDouble("total_amount", 0.0),
            paymentMethod = optNormalized(json, "payment_method").orEmpty(),
            etaText = optNormalized(json, "eta_text"),
            createdAt = optNormalized(json, "created_at"),
            customerName = optNormalized(json, "customer_name"),
            customerAddress = optNormalized(json, "customer_address"),
            assignedRiderId = optNormalized(json, "assigned_rider_id"),
            assignedToMe = json.optBoolean("assigned_to_me", false)
        )
    }

    private fun updateOrderStatus(
        token: String,
        path: String,
        callback: (Result<MobileOrder>) -> Unit
    ) {
        val orderId = extractOrderIdFromPath(path)
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", "Bearer $token")
            .put("{}".toRequestBody(jsonMediaType))
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
                    try {
                        val root = JSONObject(raw)
                        val data = root.optJSONObject("data")
                        if (data != null) {
                            callback(Result.success(parseOrder(data)))
                            return
                        }

                        if (root.optBoolean("queued", false)) {
                            if (orderId.isNullOrBlank()) {
                                callback(Result.failure(Exception("Order update queued")))
                                return
                            }
                            listMyOrders(token) { listResult ->
                                listResult.onSuccess { orders ->
                                    val match = orders.firstOrNull { it.id == orderId }
                                    if (match != null) {
                                        callback(Result.success(match))
                                    } else {
                                        callback(Result.failure(Exception("Order update queued")))
                                    }
                                }.onFailure { listError ->
                                    callback(Result.failure(Exception(listError.message ?: "Order update queued")))
                                }
                            }
                            return
                        }

                        callback(Result.failure(Exception(extractMessage(raw) ?: "Update queued")))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun updateOrderStatusBulk(
        token: String,
        path: String,
        orderIds: List<String>,
        callback: (Result<Unit>) -> Unit
    ) {
        val body = JSONObject().put("order_ids", JSONArray(orderIds)).toString()
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
                        callback(Result.failure(Exception(extractMessage(raw) ?: "Bulk update failed")))
                        return
                    }
                    callback(Result.success(Unit))
                }
            }
        })
    }

    private fun extractMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try { JSONObject(raw).optString("message", null) } catch (_: Exception) { null }
    }

    private fun optNormalized(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.opt(key)?.toString()?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.equals("null", ignoreCase = true)) return null
        if (value.equals("undefined", ignoreCase = true)) return null
        return value
    }

    private fun extractOrderIdFromPath(path: String): String? {
        val afterOrders = path.substringAfter("/orders/", "")
        if (afterOrders.isBlank()) return null
        val id = afterOrders.substringBefore("/").trim()
        return id.ifBlank { null }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
