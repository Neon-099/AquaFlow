package com.aquaflow

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.Toast

import com.aquaflow.data.Order
import com.aquaflow.data.OrderStatus
import com.google.android.material.button.MaterialButton

class OrderPage : AppCompatActivity() {
    private lateinit var activeOrdersContainer: LinearLayout
    private lateinit var recentActivityContainer: LinearLayout
    private lateinit var tvSeeAllOrders: TextView
    private lateinit var btnNewOrder: MaterialButton

    private lateinit var tvOrderTitle : TextView
    private lateinit var tvOrderTime : TextView
    private lateinit var tvItemDetails: TextView
    private lateinit var tvStatus : TextView
    private lateinit var tvDeliveryTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_orders)

        initializeViews()
        setupBottomNav()

        //SAMPLE DATA
        val allActiveOrders = List(7) {
            Order("1", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("2", "Today, 11:15 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.CONFIRMED)
            Order("3", "Today, 9:05 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("4", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("5", "Today, 11:15 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.CONFIRMED)
            Order("6", "Today, 9:05 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("7", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
        } // 7 items total
        val allRecentHistory = List(6) {
            Order("1", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("2", "Today, 11:15 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.CONFIRMED)
            Order("3", "Today, 9:05 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("4", "Today, 10:30 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY)
            Order("5", "Today, 11:15 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.CONFIRMED)
            Order("6", "Today, 9:05 AM", 2, "5-Gallon Slim Refill", "12-18 mins", OrderStatus.OUT_FOR_DELIVERY) }

        renderActiveOrders(allActiveOrders)
        renderRecentActivity(allRecentHistory)
        btnNewOrder.setOnClickListener {
            Toast.makeText(this, "Opening New Order Screen", Toast.LENGTH_SHORT).show()

        }
    }

    private fun renderActiveOrders(orders: List<Order>) {
        activeOrdersContainer.removeAllViews()

        // LIMIT RENDER TO 5
        val limit = if (orders.size > 5) 5 else orders.size


        for (i in 0 until limit) {
            val order = orders[i]
            val itemView = layoutInflater.inflate(R.layout.item_order_card, activeOrdersContainer, false)
            // Bind your data to itemView here (Status, buttons, etc.)
            tvOrderTitle.text = "Order #${order.id}"
            tvOrderTime.text = order.timeStamp // "Today, 10:30 AM"
            tvItemDetails.text = "${order.quantity}x ${order.itemName}" // "2x 5-Gallon Slim Refill"
            tvDeliveryTime.text = order.arrivalTime // "12-18 mins"
            activeOrdersContainer.addView(itemView)
        }

        // SHOW "SEE ALL" IF EXCEEDS 5
        if (orders.size > 5) {
            tvSeeAllOrders.visibility = View.VISIBLE
            tvSeeAllOrders.setOnClickListener {
                Toast.makeText(this, "Loading all ${orders.size} orders...", Toast.LENGTH_SHORT).show()
                // Here you could expand the list or go to a full list activity
            }
        }
    }

    private fun initializeViews(){
        activeOrdersContainer = findViewById(R.id.activeOrdersContainer)
        recentActivityContainer = findViewById(R.id.recentActivityContainer)
        tvSeeAllOrders = findViewById(R.id.tvSeeAllOrders)
        btnNewOrder = findViewById(R.id.btnNewOrder)

        tvOrderTitle = findViewById<TextView>(R.id.tvOrderTitle)
        tvOrderTime = findViewById<TextView>(R.id.tvOrderDate)
        tvStatus = findViewById<TextView>(R.id.tvStatusBadge)
        tvDeliveryTime = findViewById<TextView>(R.id.tvArrivalTime)
    }


    private fun renderRecentActivity(history: List<Order>) {
        recentActivityContainer.removeAllViews()

        // LIMIT RECENT TO 4
        val limit = if (history.size > 4) 4 else history.size

        for (i in 0 until limit) {
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, recentActivityContainer, false)
            // Bind time and description
            recentActivityContainer.addView(itemView)
        }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_orders
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomePage::class.java))
                    true
                }
                R.id.navigation_messages -> {
                    // Navigate to Messages
                    startActivity(Intent(this, MessagePage::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    // Navigate to Profile
                    startActivity(Intent(this, ProfilePage::class.java))
                    true
                }
                R.id.navigation_orders -> true
                else -> false
            }
        }
    }
}