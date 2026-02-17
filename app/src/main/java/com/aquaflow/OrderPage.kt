package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.ui.OrderStatusBadgeMapper
import com.aquaflow.utils.MobileOrder
import com.aquaflow.utils.OrderApi
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class OrderPage : AppCompatActivity() {
    private lateinit var activeOrdersContainer: LinearLayout
    private lateinit var recentActivityContainer: LinearLayout
    private lateinit var tvSeeAllOrders: TextView
    private lateinit var btnNewOrder: MaterialButton
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_orders)

        initializeViews()
        setupBottomNav()
        loadOrdersFromBackend()

        btnNewOrder.setOnClickListener {
            startActivity(Intent(this, OrderFormPage::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadOrdersFromBackend()
    }

    private fun initializeViews() {
        activeOrdersContainer = findViewById(R.id.activeOrdersContainer)
        recentActivityContainer = findViewById(R.id.recentActivityContainer)
        tvSeeAllOrders = findViewById(R.id.tvSeeAllOrders)
        btnNewOrder = findViewById(R.id.btnNewOrder)
        loadingOverlay = findViewById(R.id.loadingOverlay)
    }

    private fun loadOrdersFromBackend() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            setLoading(false)
            return
        }

        OrderApi.listMyOrders(token) { result ->
            runOnUiThread {
                result.onSuccess { all ->
                    val active = all.filter { !isTerminalStatus(it.status) }
                    val history = all.filter { isTerminalStatus(it.status) }
                    renderActiveOrdersFromApi(active)
                    renderRecentActivityFromApi(history)
                    tvSeeAllOrders.visibility = if (active.size > 5) View.VISIBLE else View.GONE
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to load orders", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun renderActiveOrdersFromApi(orders: List<MobileOrder>) {
        activeOrdersContainer.removeAllViews()

        for (order in orders.take(5)) {
            val itemView = layoutInflater.inflate(R.layout.item_order_card, activeOrdersContainer, false)

            val tvOrderTitle = itemView.findViewById<TextView>(R.id.tvOrderTitle)
            val tvOrderDate = itemView.findViewById<TextView>(R.id.tvOrderDate)
            val tvArrival = itemView.findViewById<TextView>(R.id.tvArrivalTime)
            val tvStatus = itemView.findViewById<TextView>(R.id.tvStatusBadge)
            val container = itemView.findViewById<LinearLayout>(R.id.orderButtonContainer)
            val btnPrimary = itemView.findViewById<MaterialButton>(R.id.btnPrimary)
            val btnSecondary = itemView.findViewById<MaterialButton>(R.id.btnSecondary)

            val orderCode = order.orderCode ?: "ORD-${order.id.takeLast(6)}"
            tvOrderTitle.text = "#$orderCode"
            tvArrival.text = order.etaText ?: fallbackArrivalText(order.status)

            val mappedStatus = OrderStatusBadgeMapper.fromServerStatus(order.status)
            OrderStatusBadgeMapper.apply(tvStatus, mappedStatus)

            applyOrderActionButtons(
                status = order.status,
                container = container,
                btnPrimary = btnPrimary,
                btnSecondary = btnSecondary,
                order = order
            )

            activeOrdersContainer.addView(itemView)
        }
    }

    private fun renderRecentActivityFromApi(history: List<MobileOrder>) {
        recentActivityContainer.removeAllViews()

        for (order in history.take(5)) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, recentActivityContainer, false)

            val icon = itemView.findViewById<ImageView>(R.id.ivActivityIcon)
            val tvTime = itemView.findViewById<TextView>(R.id.tvRecentTime)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvRecentDesc)

            val orderCode = order.orderCode ?: "ORD-${order.id.takeLast(6)}"
            tvTime.text = formatDisplayDate(order.createdAt)
            tvDesc.text = when (order.status.uppercase()) {
                "COMPLETED" -> "#$orderCode completed"
                "CANCELLED" -> "#$orderCode cancelled"
                else -> "#$orderCode ${order.status.lowercase().replace("_", " ")}"
            }

            icon.setImageResource(
                when (order.status.uppercase()) {
                    "COMPLETED" -> R.drawable.ic_check_circle
                    "CANCELLED" -> R.drawable.ic_remove
                    else -> R.drawable.ic_clock
                }
            )

            itemView.setOnClickListener {
                val intent = Intent(this, OrderTrackingPage::class.java)
                intent.putExtra("order_id", order.id)
                startActivity(intent)
            }

            recentActivityContainer.addView(itemView)
        }
    }

    private fun applyOrderActionButtons(
        status: String,
        container: LinearLayout,
        btnPrimary: MaterialButton,
        btnSecondary: MaterialButton,
        order: MobileOrder
    ) {
        when (status.uppercase()) {
            "PENDING" -> {
                container.visibility = View.VISIBLE
                btnPrimary.visibility = View.VISIBLE
                btnPrimary.text = "Track"
                btnSecondary.visibility = View.GONE
            }
            "CONFIRMED", "PICKED_UP", "OUT_FOR_DELIVERY", "DELIVERED", "PENDING_PAYMENT" -> {
                container.visibility = View.VISIBLE
                btnPrimary.visibility = View.VISIBLE
                btnPrimary.text = "Track"
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Message"
            }
            "COMPLETED", "CANCELLED" -> {
                container.visibility = View.GONE
            }
            else -> {
                container.visibility = View.GONE
            }
        }

        btnPrimary.setOnClickListener {
            val intent = Intent(this, OrderTrackingPage::class.java)
            intent.putExtra("order_id", order.id)
            startActivity(intent)
        }

        btnSecondary.setOnClickListener {
            val intent = Intent(this, MessagePage::class.java)
            intent.putExtra("order_id", order.id)
            startActivity(intent)
        }
    }

    private fun fallbackArrivalText(status: String): String {
        return when (status.uppercase()) {
            "OUT_FOR_DELIVERY" -> "On the way"
            "PICKED_UP" -> "Picked up by rider"
            "CONFIRMED" -> "Preparing order"
            "PENDING_PAYMENT" -> "Awaiting payment"
            else -> "Processing"
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

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_orders
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_orders -> true
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomePage::class.java))
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

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
