package com.aquaflow.repo

import android.content.Context
import com.aquaflow.utils.CreateOrderPayload
import com.aquaflow.utils.MobileOrder
import com.aquaflow.utils.OrderApi
import com.aquaflow.ui.OrderStatusBadgeMapper

data class OrderBuckets(
    val active: List<MobileOrder>,
    val history: List<MobileOrder>
)

data class OrderActionConfig(
    val showContainer: Boolean,
    val showPrimary: Boolean,
    val primaryText: String = "",
    val showSecondary: Boolean,
    val secondaryText: String = ""
)

class OrderRepository(private val context: Context) {

    private fun token(): String? =
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("token", null)

    fun listMyOrders(callback: (Result<List<MobileOrder>>) -> Unit) {
        val tk = token()
        if (tk.isNullOrBlank()) {
            callback(Result.failure(Exception("Missing auth token")))
            return
        }
        OrderApi.listMyOrders(tk, callback)
    }

    fun getOrder(orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        val tk = token()
        if (tk.isNullOrBlank()) {
            callback(Result.failure(Exception("Missing auth token")))
            return
        }
        OrderApi.getOrderById(tk, orderId) { result ->
            callback(result.map { it.order })
        }
    }

    fun createOrder(
        quantity: Int,
        waterTypeText: String,
        paymentMethod: String, // "COD" | "GCASH"
        totalAmount: Double,
        callback: (Result<Pair<MobileOrder, String?>>) -> Unit // second = gcash checkout url if present
    ) {
        val tk = token()
        if (tk.isNullOrBlank()) {
            callback(Result.failure(Exception("Missing auth token")))
            return
        }

        val gallonType = if (waterTypeText.contains("Slim", ignoreCase = true)) "SLIM" else "ROUND"
        val payload = CreateOrderPayload(
            waterQuantity = quantity,
            gallonType = gallonType,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod
        )
        OrderApi.createOrder(tk, payload, callback)
    }

    fun cancelOrder(orderId: String, callback: (Result<MobileOrder>) -> Unit) {
        val tk = token()
        if (tk.isNullOrBlank()) {
            callback(Result.failure(Exception("Missing auth token")))
            return
        }
        OrderApi.cancelOrder(tk, orderId, callback)
    }

    fun splitByLifecycle(orders: List<MobileOrder>): OrderBuckets {
        val active = orders.filter { !isTerminal(it.status) }
        val history = orders.filter { isTerminal(it.status) }
        return OrderBuckets(active = active, history = history)
    }

    fun currentHomeOrder(orders: List<MobileOrder>): MobileOrder? =
        orders.firstOrNull { !isTerminal(it.status) }

    fun badgeStatusOrNull(rawStatus: String) =
        OrderStatusBadgeMapper.fromServerStatus(rawStatus)

    fun actionConfig(rawStatus: String): OrderActionConfig {
        return when (rawStatus.uppercase()) {
            "PENDING" -> OrderActionConfig(
                showContainer = true,
                showPrimary = true,
                primaryText = "Track",
                showSecondary = false
            )
            "CONFIRMED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "PENDING_PAYMENT" -> OrderActionConfig(
                showContainer = true,
                showPrimary = true,
                primaryText = "Track",
                showSecondary = true,
                secondaryText = "Message"
            )
            "COMPLETED", "CANCELLED" -> OrderActionConfig(
                showContainer = false,
                showPrimary = false,
                showSecondary = false
            )
            else -> OrderActionConfig(
                showContainer = false,
                showPrimary = false,
                showSecondary = false
            )
        }
    }

    fun isTerminal(rawStatus: String): Boolean {
        val s = rawStatus.uppercase()
        return s == "COMPLETED" || s == "CANCELLED"
    }
}
