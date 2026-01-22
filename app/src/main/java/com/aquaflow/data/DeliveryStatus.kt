package com.aquaflow.data

data class DeliveryStatus(
    val userName: String,
    val orderDetails: String,
    val statusText: String,
    val arrivalTimeRange: String,
    val isRiderNearby: Boolean
)
