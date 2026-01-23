package com.aquaflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent
import android.widget.TextView
import com.google.android.material.button.MaterialButton


import com.aquaflow.data.DeliveryStatus
class HomePage : AppCompatActivity() {

    //DECLARE VIEWS
    private lateinit var tvGreeting: TextView
    private lateinit var tvOrderDetails: TextView
    private lateinit var tvStatusTag: TextView
    private lateinit var tvArrivalTimer: TextView
    private lateinit var tvRiderStatus: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_page)

        //INITIALIZE VIEWS BY ID
        tvGreeting = findViewById(R.id.tvGreeting)
        tvOrderDetails = findViewById(R.id.tvOrderDetails)
        tvStatusTag = findViewById(R.id.tvStatusTag)
        tvRiderStatus = findViewById(R.id.tvRiderStatus)
        tvArrivalTimer = findViewById(R.id.tvArrivalTimer)

        //SIMULATE GETTING DATA FROM A SERVER
        val mockData = DeliveryStatus(
            userName = "Alex",
            orderDetails = "2x 5-Gallon Slim Refill",
            statusText = "Out for Delivery",
            arrivalTimeRange = "12-18 mins",
            isRiderNearby = true
        )

        // 1. Initialize the Buttons
        val btnTrackDelivery = findViewById<MaterialButton>(R.id.btnTrackDelivery)
        val btnMessageStation = findViewById<MaterialButton>(R.id.btnMessageStation)

        // 2. Set Click Listener for Track Delivery
        btnTrackDelivery.setOnClickListener {
            val intent = Intent(this, DeliveryPage::class.java)
            startActivity(intent)
        }

        // 3. Set Click Listener for Message Station
        btnMessageStation.setOnClickListener {
            val intent = Intent(this, MessagePage::class.java)
            startActivity(intent)
        }

        // Initialize Bottom Nav as we did before
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_orders -> {
                    // You can also use Intents here to switch between main tabs
                    // startActivity(Intent(this, OrdersActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Handle navigation clicks
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Already here
                    true
                }
                R.id.navigation_orders -> {
                    // Navigate to Orders
                    true
                }
                R.id.navigation_messages -> {
                    // Navigate to Messages
                    true
                }
                R.id.navigation_profile -> {
                    // Navigate to Profile
                    true
                }
                else -> false
            }
        }
    }
}