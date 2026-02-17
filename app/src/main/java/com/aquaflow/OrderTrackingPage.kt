package com.aquaflow

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.ui.OrderStatusBadgeMapper
import com.aquaflow.utils.OrderApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderTrackingPage : AppCompatActivity() {
    private lateinit var trackingStepContainer: LinearLayout
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvArrivingIn: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var loadingOverlay: View

    private val pollHandler = Handler(Looper.getMainLooper())
    private var orderId: String = ""

    private data class TrackingStep(
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val statusKey: String
    )

    private val steps = listOf(
        TrackingStep("Confirmed", "Order validated by system", R.drawable.ic_confirmed, "CONFIRMED"),
        TrackingStep("Gallon Pickup", "Rider collected empty gallons", R.drawable.ic_pickup, "PICKED_UP"),
        TrackingStep("Refilling In Progress", "Station is refilling your order", R.drawable.ic_process, "PICKED_UP"),
        TrackingStep("Delivery In Progress", "Rider is en route to you", R.drawable.ic_delivery_truck, "OUT_FOR_DELIVERY"),
        TrackingStep("Delivered", "Rider marked order as delivered", R.drawable.ic_delivered, "DELIVERED"),
        TrackingStep("Pending Payment", "Awaiting COD/GCASH confirmation", R.drawable.ic_payment, "PENDING_PAYMENT"),
        TrackingStep("Completed", "Order fulfilled and closed", R.drawable.ic_check_circle, "COMPLETED")
    )

    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchOrderAndRender()
            pollHandler.postDelayed(this, 8000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_tracking_page)

        initializeViews()
        orderId = intent.getStringExtra("order_id").orEmpty()

        val fallbackOrderCode = intent.getStringExtra("order_code")
        tvOrderNumber.text = if (fallbackOrderCode.isNullOrBlank()) "Order" else "Order #$fallbackOrderCode"

        if (orderId.isBlank()) {
            tvArrivingIn.text = "Order reference not found"
            renderTrackingSteps("PENDING")
            setLoading(false)
            return
        }

        fetchOrderAndRender()
    }

    override fun onStart() {
        super.onStart()
        if (orderId.isNotBlank()) pollHandler.postDelayed(pollRunnable, 8000L)
    }

    override fun onStop() {
        super.onStop()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun initializeViews() {
        trackingStepContainer = findViewById(R.id.trackingStepContainer)
        tvOrderNumber = findViewById(R.id.tvOrderNumber)
        tvArrivingIn = findViewById(R.id.tvArrivingIn)
        tvLastUpdated = findViewById(R.id.tvLastUpdated)
        btnBack = findViewById(R.id.btnBack)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        btnBack.setOnClickListener { finish() }
    }

    private fun fetchOrderAndRender() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            setLoading(false)
            return
        }

        OrderApi.getOrderById(token, orderId) { result ->
            runOnUiThread {
                result.onSuccess { details ->
                    val order = details.order
                    val code = order.orderCode ?: order.id.takeLast(6)
                    tvOrderNumber.text = "Order #$code"

                    tvArrivingIn.text = when {
                        !order.etaText.isNullOrBlank() -> "Arriving in ${order.etaText}"
                        order.status.equals("PENDING_PAYMENT", true) -> "Awaiting payment confirmation"
                        order.status.equals("COMPLETED", true) -> "Order completed"
                        else -> "Status: ${order.status.replace("_", " ")}"
                    }

                    tvLastUpdated.text = "Last updated: ${nowTime()}"
                    renderTrackingSteps(order.status.uppercase(Locale.ROOT))
                    setLoading(false)
                }.onFailure {
                    tvLastUpdated.text = "Last updated: ${nowTime()}"
                    setLoading(false)
                }
            }
        }
    }

    private fun renderTrackingSteps(currentStatus: String) {
        trackingStepContainer.removeAllViews()
        val currentIndex = statusToStepIndex(currentStatus)

        for (i in steps.indices) {
            val step = steps[i]
            val itemView = layoutInflater.inflate(R.layout.item_tracking_step, trackingStepContainer, false)

            val lineTop = itemView.findViewById<View>(R.id.lineTop)
            val lineBottom = itemView.findViewById<View>(R.id.lineBottom)
            val iconBg = itemView.findViewById<View>(R.id.viewIconBg)
            val icon = itemView.findViewById<ImageView>(R.id.ivStepIcon)
            val title = itemView.findViewById<TextView>(R.id.tvStepTitle)
            val subtitle = itemView.findViewById<TextView>(R.id.tvStepSubtitle)

            title.text = step.title
            subtitle.text = step.subtitle
            icon.setImageResource(step.iconRes)

            when {
                i < currentIndex -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_completed)
                    icon.setColorFilter(Color.WHITE)
                    title.setTextColor(Color.parseColor("#4CAF50"))
                    subtitle.setTextColor(Color.GRAY)
                    lineTop.setBackgroundColor(Color.parseColor("#4CAF50"))
                    lineBottom.setBackgroundColor(Color.parseColor("#4CAF50"))
                }
                i == currentIndex -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_current)
                    icon.setColorFilter(Color.parseColor("#2196F3"))
                    title.setTextColor(Color.parseColor("#2196F3"))
                    subtitle.setTextColor(Color.GRAY)
                    lineTop.setBackgroundColor(if (i > 0) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0"))
                    lineBottom.setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
                else -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_inactive)
                    icon.setColorFilter(Color.parseColor("#BDBDBD"))
                    title.setTextColor(Color.parseColor("#BDBDBD"))
                    subtitle.setTextColor(Color.parseColor("#E0E0E0"))
                    lineTop.setBackgroundColor(Color.parseColor("#E0E0E0"))
                    lineBottom.setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
            }

            if (i == 0) lineTop.visibility = View.INVISIBLE
            if (i == steps.size - 1) lineBottom.visibility = View.INVISIBLE

            trackingStepContainer.addView(itemView)
        }
    }

    private fun statusToStepIndex(status: String): Int {
        return when (status) {
            "CONFIRMED" -> 0
            "PICKED_UP" -> 2
            "OUT_FOR_DELIVERY" -> 3
            "DELIVERED" -> 4
            "PENDING_PAYMENT" -> 5
            "COMPLETED" -> 6
            "CANCELLED" -> 6
            else -> 0
        }
    }

    private fun nowTime(): String =
        SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date())

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
