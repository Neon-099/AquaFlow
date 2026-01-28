package com.aquaflow

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.aquaflow.data.OrderStatus

class OrderTrackingPage : AppCompatActivity() {
    private lateinit var trackingStepContainer: LinearLayout
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvArrivingIn: TextView
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.order_tracking_page)

        initializeViews()

        // SIMULATED DATA FROM SERVER
        // Try changing this to OrderStatus.COMPLETED to see the automatic update logic
        val currentStatus = OrderStatus.OUT_FOR_DELIVERY
        val orderNum = "#248291"
        val arrivalTime = "12-18 mins"

        tvOrderNumber.text = "Order $orderNum"
        tvArrivingIn.text = "Arriving in $arrivalTime"

        renderTrackingSteps(currentStatus)
    }

    private fun initializeViews() {
        trackingStepContainer = findViewById(R.id.trackingStepContainer)
        tvOrderNumber = findViewById(R.id.tvOrderNumber)
        tvArrivingIn = findViewById(R.id.tvArrivingIn)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
    }

    private fun getStatusIndex(status: OrderStatus): Int {
        return when (status) {
            OrderStatus.CONFIRMED -> 0
            OrderStatus.GALLON_PICKUP -> 1
            OrderStatus.OUT_FOR_DELIVERY -> 2
            OrderStatus.PENDING_PAYMENT -> 3
            OrderStatus.COMPLETED -> 4 // Logic: Payment confirmed -> Auto Completed
            else -> -1 // For PENDING or CANCELLED
        }
    }

    private fun renderTrackingSteps(currentStatus: OrderStatus) {
        trackingStepContainer.removeAllViews()

        // Definitions for the 5 steps in your timeline
        val steps = listOf(
            Triple("Confirmed", "Order validated by system", R.drawable.ic_confirmed),
            Triple("Pickup", "Rider collected items", R.drawable.ic_pickup),
            Triple("Out for Delivery", "Rider is en route to you", R.drawable.ic_process),
            Triple("Pending Payment", "Awaiting COD or GCash", R.drawable.ic_payment),
            Triple("Completed", "Order fulfilled and closed", R.drawable.ic_delivered)
        )

        val currentIndex = getStatusIndex(currentStatus)

        for (i in steps.indices) {
            val (titleText, subtitleText, iconRes) = steps[i]
            val itemView = layoutInflater.inflate(R.layout.item_tracking_step, trackingStepContainer, false)

            val lineTop = itemView.findViewById<View>(R.id.lineTop)
            val lineBottom = itemView.findViewById<View>(R.id.lineBottom)
            val iconBg = itemView.findViewById<View>(R.id.viewIconBg)
            val icon = itemView.findViewById<ImageView>(R.id.ivStepIcon)
            val title = itemView.findViewById<TextView>(R.id.tvStepTitle)
            val subtitle = itemView.findViewById<TextView>(R.id.tvStepSubtitle)

            title.text = titleText
            subtitle.text = subtitleText
            icon.setImageResource(iconRes)

            // --- DYNAMIC STYLING LOGIC ---
            when {
                // 1. COMPLETED STEPS (Green)
                i < currentIndex || (currentStatus == OrderStatus.COMPLETED && i == 4) -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_completed)
                    icon.setColorFilter(Color.WHITE)
                    title.setTextColor(Color.parseColor("#4CAF50"))
                    subtitle.setTextColor(Color.GRAY)
                    lineTop.setBackgroundColor(Color.parseColor("#4CAF50"))
                    lineBottom.setBackgroundColor(Color.parseColor("#4CAF50"))
                }
                // 2. CURRENT STEP (Blue Outline)
                i == currentIndex -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_current)
                    icon.setColorFilter(Color.parseColor("#2196F3"))
                    title.setTextColor(Color.parseColor("#2196F3"))
                    subtitle.setTextColor(Color.GRAY)
                    // Line from previous step should be green
                    lineTop.setBackgroundColor(if (i > 0) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0"))
                    lineBottom.setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
                // 3. FUTURE STEPS (Grey)
                else -> {
                    iconBg.setBackgroundResource(R.drawable.bg_circle_inactive)
                    icon.setColorFilter(Color.parseColor("#BDBDBD"))
                    title.setTextColor(Color.parseColor("#BDBDBD"))
                    subtitle.setTextColor(Color.parseColor("#E0E0E0"))
                    lineTop.setBackgroundColor(Color.parseColor("#E0E0E0"))
                    lineBottom.setBackgroundColor(Color.parseColor("#E0E0E0"))
                }
            }

            // HIDE TOP LINE FOR FIRST ITEM & BOTTOM LINE FOR LAST ITEM
            if (i == 0) lineTop.visibility = View.INVISIBLE
            if (i == steps.size - 1) lineBottom.visibility = View.INVISIBLE

            trackingStepContainer.addView(itemView)
        }
    }
}