package com.aquaflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import android.widget.Toast

import com.aquaflow.data.HomeOrderData
import com.aquaflow.data.OrderStatus
import com.aquaflow.data.RecentOrder

class HomePage : AppCompatActivity() {

    //DECLARE VIEWS
    private lateinit var tvGreeting: TextView
    private lateinit var cardOrder: MaterialCardView
    private lateinit var tvOrderDetails: TextView
    private lateinit var tvStatusTag: TextView
    private lateinit var statusBox: LinearLayout
    private lateinit var tvArrivalIn: TextView
    private lateinit var btnTrackDelivery: MaterialButton
    private lateinit var btnMessageStation: MaterialButton

    private lateinit var recentActivityContainer: LinearLayout
    private lateinit var tvSeeMore: TextView

    private var hideCardRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.page_home)
        } catch (e: Exception) {
            android.util.Log.e("HomePage", "Error setting content view: ${e.message}", e)
            throw e
        }

        try {
            initializeViews()
        } catch (e: Exception) {
            android.util.Log.e("HomePage", "Error initializing views: ${e.message}", e)
            throw e
        }


        //SIMULATE GETTING DATA FROM A SERVER
        val mockData = HomeOrderData(
            status = OrderStatus.OUT_FOR_DELIVERY,
            orderNumber = "#123456",
            quantityText = "2 x 5-Gallon Slim Refill",
            arrivalTime = " 12-18 mins"
        )
        val recentDataFromServer = listOf(
            RecentOrder("#121124", "Yesterday 4:20 PM", "Delivered: 3× 5-Gallon"),
            RecentOrder("#123123", "2 days ago 1:15 PM", "Delivered: 1× 5-Gallon Slim"),
            RecentOrder("#121353", "Last week", "Delivered: 5× 5-Gallon"),
            RecentOrder("#231244", "Jan 20, 11:00 AM", "Delivered: 1× 5-Gallon") // This won't render
        )
        //APPLY CONDITIONAL RENDERING
        renderCurrentOrder(mockData)

        //SET GREETING (SAMPLE)
        tvGreeting.text = "Hi, Emman"

        //SET CLICK LISTENER (FOR FUNCTIONALITY)
        btnTrackDelivery.setOnClickListener {
            val intent = Intent(this, OrderTrackingPage::class.java)
            startActivity(intent)
        }
        btnMessageStation.setOnClickListener {
            val intent = Intent(this, MessagePage::class.java)
            startActivity(intent)
        }

        renderRecentActivity(recentDataFromServer)
        setupBottomNavigation()
    }

    private fun initializeViews(){
        tvGreeting = findViewById(R.id.tvGreeting)
        cardOrder = findViewById(R.id.cardOrder)
        tvOrderDetails = findViewById(R.id.tvOrderDetails)
        tvStatusTag = findViewById(R.id.tvStatusTag)
        statusBox = findViewById(R.id.statusBox)
        tvArrivalIn = findViewById(R.id.tvArrivalIn)
        btnTrackDelivery = findViewById(R.id.btnTrackDelivery)
        btnMessageStation = findViewById(R.id.btnMessageStation)

        recentActivityContainer = findViewById(R.id.recentActivityContainer)
        tvSeeMore = findViewById(R.id.btnSeeMore)
    }

    private fun renderCurrentOrder(order: HomeOrderData?) {
        if (order == null || order.status == null) {
            //HIDE EVERYTHING (if no order exists)
            cardOrder.visibility = View.GONE
            toggleActionButtons(View.GONE)
        } else {
            //SHOW CARD
            cardOrder.visibility = View.VISIBLE
            tvOrderDetails.text = order.quantityText
            //RENDER DYNAMIC BADGE
            renderStatusBadge(order.status)

            //RENDER ARRIVAL TIME LOGIC
            if (order.status == OrderStatus.COMPLETED) {
                //SHOW THE COMPLETED STATE FOR THE MEAN TIME
                statusBox.visibility = View.GONE // HIDE ARRIVAL TIME FOR COMPLETED

                //START COUNTDOWN (after rendering the completed badge)
                hideCardRunnable?.let { cardOrder.removeCallbacks(it) }
                hideCardRunnable = Runnable {
                    val destroyed = android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed
                    if (!isFinishing && !destroyed && cardOrder.isAttachedToWindow) {
                        cardOrder.animate().alpha(0f).setDuration(500).withEndAction{
                            //CHECK AGAIN IF THE ORDER IS STILL COMPLETED BEFORE HIDING
                            cardOrder.visibility = View.GONE
                            toggleActionButtons(View.GONE)
                        }
                    }
                }
                cardOrder.postDelayed(hideCardRunnable!!, 60000)
            }   else if (order.status == OrderStatus.OUT_FOR_DELIVERY){
                statusBox.visibility = View.VISIBLE
                tvArrivalIn.text = "Arriving in ${order.arrivalTime}"
                toggleActionButtons(View.GONE)
            }   else {
                statusBox.visibility = View.GONE
                toggleActionButtons(View.GONE)
            }
        }
    }

    //HELPER TO TOGGLE BOTH BUTTONS AT ONCE
    private fun toggleActionButtons(visibility: Int) {
        btnTrackDelivery.visibility = visibility
        btnMessageStation.visibility = visibility
    }

    private fun renderStatusBadge(status: OrderStatus) {
        when (status) {
            OrderStatus.PENDING -> {
                tvStatusTag.visibility = View.VISIBLE
                tvStatusTag.text = "Pending"
                tvStatusTag.setBackgroundResource(R.drawable.bg_badge_amber)
                tvStatusTag.setTextColor(Color.WHITE)
            }
            OrderStatus.CONFIRMED -> {
                tvStatusTag.visibility = View.VISIBLE
                tvStatusTag.text = "Confirmed"
                tvStatusTag.setBackgroundResource(R.drawable.bg_badge_blue)
                tvStatusTag.setTextColor(Color.WHITE)
            }
            OrderStatus.GALLON_PICKUP -> {
                tvStatusTag.visibility = View.VISIBLE
                tvStatusTag.text = "Pickup in progress"
                tvStatusTag.setBackgroundResource(R.drawable.bg_badge_orange_light)
                tvStatusTag.setTextColor(Color.WHITE)
            }
            OrderStatus.OUT_FOR_DELIVERY -> {
                tvStatusTag.visibility = View.VISIBLE
                tvStatusTag.text = "Out for Delivery"
                tvStatusTag.setBackgroundResource(R.drawable.bg_badge_blue_solid)
                tvStatusTag.setTextColor(Color.WHITE)
            }
            OrderStatus.COMPLETED -> {
                tvStatusTag.visibility = View.GONE // Don't show badge on home if finished
            }
            else -> tvStatusTag.visibility = View.GONE
        }
    }

    private fun renderRecentActivity(data: List<RecentOrder>) {
        recentActivityContainer.removeAllViews()

        //ENFORCING THE 3-ITEM LIMIT
        val displayLimit = if (data.size > 3) 3 else data.size

        for (i in 0 until displayLimit){
            val item = data[i]
            val itemView = layoutInflater.inflate(R.layout.item_recent_activity, recentActivityContainer, false)

            //BIND DATA TO THE REUSED TEMPLATE (null-safe in case IDs differ)
            val timeView = itemView.findViewById<TextView?>(R.id.tvRecentTime)
            val descView = itemView.findViewById<TextView?>(R.id.tvRecentDesc)
            timeView?.text = item.time
            descView?.text = item.description

            //MAKE TEXT "see more" FUNCTIONABLE
            descView?.setOnClickListener {
                Toast.makeText(this, "Opening summary for ${item.time}", Toast.LENGTH_SHORT).show()
            }

            recentActivityContainer.addView(itemView)
        }

        //SEE MORE LOGIC
        if (data.size > 3) {
            tvSeeMore.visibility = View.VISIBLE
            tvSeeMore.setOnClickListener {
                // Navigate to the full Orders/Delivery Page
                val intent = Intent(this, OrderPage::class.java)
                startActivity(intent)
            }
        } else {
            tvSeeMore.visibility = View.GONE
        }
    }
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        // Handle navigation clicks
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java))
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
                else -> false
            }
        }
    }

    //REMOVE ANY PENDING TASKS WHEN THE ACTIVITY IS DESTROYED
    override fun onDestroy(){
        super.onDestroy()
        hideCardRunnable?.let { cardOrder.removeCallbacks(it) }
        hideCardRunnable = null
    }
}
