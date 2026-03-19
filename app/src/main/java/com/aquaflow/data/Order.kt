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

