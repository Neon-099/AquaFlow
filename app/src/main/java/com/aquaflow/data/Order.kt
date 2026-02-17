package com.aquaflow.data

data class Order(
    val id: String,
    val timeStamp: String,
    val quantity: Int,
    val itemName: String,
    val arrivalTime: String,
    val status: OrderStatus
)

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PENDING_PAYMENT,
    COMPLETED,
    CANCELLED
}

data class HomeOrderData(
    val status: OrderStatus?, //NULL IF NO ACTIVE ORDER
    val orderNumber: String,
    val quantityText: String,
    val arrivalTime: String,
)

//DATA CLASS FOR RECENT ACTIVITY
data class RecentOrder(
    val orderNumber: String,
    val time: String,
    val description: String
)

