package com.aquaflow

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.ui.OrderStatusBadgeMapper
import com.aquaflow.utils.MobileOrder
import com.aquaflow.utils.OrderApi
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class HomePage : AppCompatActivity() {

    private lateinit var tvGreeting: TextView
    private lateinit var cardOrder: MaterialCardView
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvOrderDetails: TextView
    private lateinit var tvStatusTag: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var tvArrivalIn: TextView
    private lateinit var actionButtonsContainer: LinearLayout
    private lateinit var btnTrackDelivery: MaterialButton
    private lateinit var btnMessageStation: MaterialButton
    private lateinit var emptyStateContainer: LinearLayout

    private lateinit var recentActivityContainer: LinearLayout
    private lateinit var tvSeeMore: TextView
    private lateinit var loadingOverlay: View

    private var hideCardRunnable: Runnable? = null
    private var currentOrderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_home)

        initializeViews()
        setupBottomNavigation()
        setupActionButtons()

        val name = getSharedPreferences("auth", MODE_PRIVATE).getString("name", null)
        tvGreeting.text = if (name.isNullOrBlank()) "Hi there" else "Hi, $name"
    }

    override fun onResume() {
        super.onResume()
        loadHomeDataFromBackend()
    }

    private fun initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting)
        cardOrder = findViewById(R.id.cardOrder)
        tvOrderNumber = findViewById(R.id.tvOrderNumber)
        tvOrderDetails = findViewById(R.id.tvOrderDetails)
        tvStatusTag = findViewById(R.id.tvStatusTag)
        statusBox = findViewById(R.id.statusBox)
        tvArrivalIn = findViewById(R.id.tvArrivalIn)
        actionButtonsContainer = findViewById(R.id.actionButtonsContainer)
        btnTrackDelivery = findViewById(R.id.btnTrackDelivery)
        btnMessageStation = findViewById(R.id.btnMessageStation)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)

        recentActivityContainer = findViewById(R.id.recentActivityContainer)
        tvSeeMore = findViewById(R.id.btnSeeMore)
        loadingOverlay = findViewById(R.id.loadingOverlay)
    }

    private fun setupActionButtons() {
        btnTrackDelivery.setOnClickListener {
            val orderId = currentOrderId ?: return@setOnClickListener
            val intent = Intent(this, OrderTrackingPage::class.java)
            intent.putExtra("order_id", orderId)
            startActivity(intent)
        }

        btnMessageStation.setOnClickListener {
            val orderId = currentOrderId ?: return@setOnClickListener
            val intent = Intent(this, MessagePage::class.java)
            intent.putExtra("order_id", orderId)
            startActivity(intent)
        }
    }

    private fun loadHomeDataFromBackend() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            renderCurrentOrder(null)
            renderRecentActivityFromApi(emptyList())
            setLoading(false)
            return
        }

        OrderApi.listMyOrders(token) { result ->
            runOnUiThread {
                result.onSuccess { orders ->
                    val active = orders.filter { !isTerminalStatus(it.status) }
                    val history = orders.filter { isTerminalStatus(it.status) }

                    renderCurrentOrder(active.firstOrNull())
                    renderRecentActivityFromApi(history)
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to load home data", Toast.LENGTH_LONG).show()
                    renderCurrentOrder(null)
                    renderRecentActivityFromApi(emptyList())
                    setLoading(false)
                }
            }
        }
    }

    private fun renderCurrentOrder(order: MobileOrder?) {
        hideCardRunnable?.let { cardOrder.removeCallbacks(it) }
        hideCardRunnable = null

        if (order == null) {
            currentOrderId = null
            cardOrder.alpha = 1f
            cardOrder.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.VISIBLE

            tvOrderNumber.visibility = View.GONE
            tvOrderDetails.visibility = View.GONE
            tvStatusTag.visibility = View.GONE
            statusBox.visibility = View.GONE
            actionButtonsContainer.visibility = View.GONE
            return
        }

        currentOrderId = order.id
        cardOrder.alpha = 1f
        cardOrder.visibility = View.VISIBLE
        emptyStateContainer.visibility = View.GONE

        val orderCode = order.orderCode ?: "ORD-${order.id.takeLast(6)}"
        tvOrderNumber.visibility = View.VISIBLE
        tvOrderNumber.text = "#$orderCode"

        tvOrderDetails.visibility = View.VISIBLE
        tvOrderDetails.text = "${order.quantity} x ${formatGallonType(order.gallonType)}"

        tvStatusTag.visibility = View.VISIBLE
        OrderStatusBadgeMapper.apply(tvStatusTag, OrderStatusBadgeMapper.fromServerStatus(order.status))

        if (!order.etaText.isNullOrBlank() &&
            (order.status.equals("OUT_FOR_DELIVERY", true) || order.status.equals("PICKED_UP", true))
        ) {
            statusBox.visibility = View.VISIBLE
            tvArrivalIn.text = "Arriving in ${order.etaText}"
        } else {
            statusBox.visibility = View.GONE
        }

        applyActionButtons(order.status)

        if (order.status.equals("COMPLETED", true)) {
            hideCardRunnable = Runnable {
                val destroyed = Build.VERSION.SDK_INT >= 17 && isDestroyed
                if (!isFinishing && !destroyed && cardOrder.isAttachedToWindow) {
                    cardOrder.animate().alpha(0f).setDuration(500).withEndAction {
                        cardOrder.visibility = View.GONE
                        actionButtonsContainer.visibility = View.GONE
                    }
                }
            }
            cardOrder.postDelayed(hideCardRunnable!!, 60000)
        }
    }

    private fun applyActionButtons(status: String) {
        when (status.uppercase()) {
            "PENDING" -> {
                actionButtonsContainer.visibility = View.VISIBLE
                btnTrackDelivery.visibility = View.VISIBLE
                btnTrackDelivery.text = "Track"
                btnMessageStation.visibility = View.GONE
            }
            "CONFIRMED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "PENDING_PAYMENT" -> {
                actionButtonsContainer.visibility = View.VISIBLE
                btnTrackDelivery.visibility = View.VISIBLE
                btnTrackDelivery.text = "Track"
                btnMessageStation.visibility = View.VISIBLE
                btnMessageStation.text = "Message"
            }
            "COMPLETED", "CANCELLED" -> {
                actionButtonsContainer.visibility = View.GONE
            }
            else -> {
                actionButtonsContainer.visibility = View.GONE
            }
        }
    }

    private fun renderRecentActivityFromApi(history: List<MobileOrder>) {
        recentActivityContainer.removeAllViews()
        val top = history.take(3)

        for (order in top) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, recentActivityContainer, false)
            val timeView = itemView.findViewById<TextView>(R.id.tvRecentTime)
            val descView = itemView.findViewById<TextView>(R.id.tvRecentDesc)

            val orderCode = order.orderCode ?: "ORD-${order.id.takeLast(6)}"
            timeView.text = formatDisplayDate(order.createdAt)
            descView.text = when (order.status.uppercase()) {
                "COMPLETED" -> "Order #$orderCode completed"
                "CANCELLED" -> "Order #$orderCode cancelled"
                else -> "Order #$orderCode ${order.status.lowercase().replace("_", " ")}"
            }

            itemView.setOnClickListener {
                val intent = Intent(this, OrderTrackingPage::class.java)
                intent.putExtra("order_id", order.id)
                startActivity(intent)
            }

            recentActivityContainer.addView(itemView)
        }

        if (history.size > 3) {
            tvSeeMore.visibility = View.VISIBLE
            tvSeeMore.setOnClickListener {
                startActivity(Intent(this, OrderPage::class.java))
            }
        } else {
            tvSeeMore.visibility = View.GONE
        }
    }

    private fun formatGallonType(gallonType: String): String {
        return when (gallonType.uppercase()) {
            "SLIM" -> "5-Gallon Slim Refill"
            "ROUND" -> "5-Gallon Round Refill"
            else -> gallonType
        }
    }

    private fun isTerminalStatus(status: String): Boolean {
        val s = status.uppercase()
        return s == "COMPLETED" || s == "CANCELLED"
    }

    private fun formatDisplayDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "Just now"
        return raw
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_home
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java))
                    true
                }
                R.id.navigation_messages -> {
                    startActivity(Intent(this, MessagePage::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfilePage::class.java))
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideCardRunnable?.let { cardOrder.removeCallbacks(it) }
        hideCardRunnable = null
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
