package com.aquaflow

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.data.Order
import com.aquaflow.data.OrderStatus
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class OrderPage : AppCompatActivity() {
    private lateinit var activeOrdersContainer: LinearLayout
    private lateinit var recentActivityContainer: LinearLayout
    private lateinit var tvSeeAllOrders: TextView
    private lateinit var btnNewOrder: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_orders)

        initializeViews()
        setupBottomNav()

        // 1. YOUR SAMPLE DATA
        val allActiveOrders = listOf(
            Order("101", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY),
            Order("102", "Today, 11:15 AM", 1, "5-Gallon Round", "20-25 mins", OrderStatus.CONFIRMED),
            Order("103", "Today, 9:05 AM", 3, "Slim Refill", "5-10 mins", OrderStatus.OUT_FOR_DELIVERY)
        )

        val allRecentHistory = listOf(
            Order("99", "Yesterday", 2, "5-Gallon Slim", "Completed", OrderStatus.COMPLETED),
            Order("98", "2 days ago", 1, "5-Gallon Slim", "Completed", OrderStatus.COMPLETED)
        )

        // 2. CALL THE RENDER FUNCTIONS
        renderActiveOrders(allActiveOrders)
        renderRecentActivity(allRecentHistory)

        btnNewOrder.setOnClickListener {
            Toast.makeText(this, "Opening New Order Screen", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, OrderFormPage::class.java)
            startActivity(intent)
        }
    }

    private fun initializeViews() {
        activeOrdersContainer = findViewById(R.id.activeOrdersContainer)
        recentActivityContainer = findViewById(R.id.recentActivityContainer)
        tvSeeAllOrders = findViewById(R.id.tvSeeAllOrders)
        btnNewOrder = findViewById(R.id.btnNewOrder)
    }

    private fun renderActiveOrders(orders: List<Order>) {
        activeOrdersContainer.removeAllViews()

        // Limit to top 5
        val limit = if (orders.size > 5) 5 else orders.size

        for (i in 0 until limit) {
            val order = orders[i]

            // INFLATE the card layout
            val itemView = layoutInflater.inflate(R.layout.item_order_card, activeOrdersContainer, false)

            // FIND views within this specific itemView
            val tvOrderTitle = itemView.findViewById<TextView>(R.id.tvOrderTitle)
            val tvOrderTime = itemView.findViewById<TextView>(R.id.tvOrderDate)
//            val tvItemDetails = itemView.findViewById<TextView>(R.id.tvItemDetails)
            val tvDeliveryTime = itemView.findViewById<TextView>(R.id.tvArrivalTime)
            val tvStatusBadge = itemView.findViewById<TextView>(R.id.tvStatusBadge)

            // BIND the sample data to the views
            tvOrderTitle.text = "Order #${order.id}"
            tvOrderTime.text = order.timeStamp
//            tvItemDetails.text = "${order.quantity}x ${order.itemName}"
            tvDeliveryTime.text = order.arrivalTime

            // Dynamic Status Styling (Similar to your HomePage logic)
            applyStatusStyle(tvStatusBadge, order.status)

            // Add the fully populated card to the container
            activeOrdersContainer.addView(itemView)
        }

        // Show "See All" if there are more than 5
        tvSeeAllOrders.visibility = if (orders.size > 5) View.VISIBLE else View.GONE
    }

    private fun renderRecentActivity(history: List<Order>) {
        recentActivityContainer.removeAllViews()

        for (order in history) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, recentActivityContainer, false)

            // Bind simple history data
            val tvTime = itemView.findViewById<TextView>(R.id.tvRecentTime)
            val tvDesc = itemView.findViewById<TextView>(R.id.tvRecentDesc)

            tvTime.text = order.timeStamp
            tvDesc.text = "Delivered: ${order.quantity}x ${order.itemName}"

            recentActivityContainer.addView(itemView)
        }
    }

    // Helper to style the status badges dynamically
    private fun applyStatusStyle(view: TextView, status: OrderStatus) {
        view.text = status.name.replace("_", " ")
        when (status) {
            OrderStatus.OUT_FOR_DELIVERY -> {
                view.setBackgroundResource(R.drawable.bg_badge_blue_solid)
                view.setTextColor(Color.WHITE)
            }
            OrderStatus.CONFIRMED -> {
                view.setBackgroundResource(R.drawable.bg_badge_blue)
                view.setTextColor(Color.WHITE)
            }
            else -> {
                view.setBackgroundResource(R.drawable.bg_badge_amber)
                view.setTextColor(Color.WHITE)
            }
        }
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
                    startActivity(Intent(this, HomePage::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, HomePage::class.java))
                    true
                }
                else -> false
            }
        }
    }
}