package com.aquaflow.ui

data class OrderActionState(
    val showContainer: Boolean,
    val showPrimary: Boolean,
    val primaryText: String = "",
    val showSecondary: Boolean,
    val secondaryText: String = ""
)

object OrderLifecycleUi {
    fun actionState(status: String): OrderActionState {
        return when (status.uppercase()) {
            "PENDING" -> OrderActionState(true, true, "Track", false)
            "CONFIRMED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "PENDING_PAYMENT" ->
                OrderActionState(true, true, "Track", true, "Message")
            "COMPLETED", "CANCELLED" -> OrderActionState(false, false, "", false)
            else -> OrderActionState(false, false, "", false)
        }
    }

    fun isActive(status: String): Boolean {
        return status.uppercase() !in setOf("COMPLETED", "CANCELLED")
    }
}
