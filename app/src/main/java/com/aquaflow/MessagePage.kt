package com.aquaflow

import android.content.Intent
import android.widget.ImageView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

data class MessageThread(
    val senderName: String,
    val preview: String,
    val time: String,
    val orderNumber: String? = null,
    val isUnread: Boolean = false,
    val imageRes: Int = R.drawable.ic_profile
)

class MessagePage : AppCompatActivity(){
    private lateinit var messagesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_message)

        messagesContainer = findViewById(R.id.messagesContainer)

        // MOCK DATA FROM SERVER
        val threads = listOf(
            MessageThread("Mike (Rider)", "I've arrived at the gate.", "2m ago", "Order #2841", true),
            MessageThread("Water Station Support", "Your refill schedule has been updated.", "1h ago"),
            MessageThread("John (Driver)", "Thanks! Enjoy your water.", "Yesterday"),
            MessageThread("System Update", "Service maintenance scheduled for Sunday.", "2d ago", null, false, R.drawable.ic_notification_bell)
        )

        renderMessages(threads)
        setupBottomNav()
    }

    private fun renderMessages(data: List<MessageThread>) {
        messagesContainer.removeAllViews()

        for (thread in data) {
            val itemView = layoutInflater.inflate(R.layout.item_message_row, messagesContainer, false)

            itemView.findViewById<TextView>(R.id.tvSenderName).text = thread.senderName
            itemView.findViewById<TextView>(R.id.tvMessagePreview).text = thread.preview
            itemView.findViewById<TextView>(R.id.tvMessageTime).text = thread.time
            itemView.findViewById<ImageView>(R.id.ivSenderProfile).setImageResource(thread.imageRes)

            // Dynamic Order Badge
            val badge = itemView.findViewById<TextView>(R.id.tvOrderBadge)
            if (thread.orderNumber != null) {
                badge.visibility = View.VISIBLE
                badge.text = thread.orderNumber
            }

            // Dynamic Unread Indicator
            val dot = itemView.findViewById<View>(R.id.viewUnreadDot)
            dot.visibility = if (thread.isUnread) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener {
                // Open Chat Activity
            }

            messagesContainer.addView(itemView)
        }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_messages
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomePage::class.java))
                    true
                }
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java))
                    true
                }
                R.id.navigation_profile -> {
                    // Navigate to Profile
                    startActivity(Intent(this, ProfilePage::class.java))
                    true
                }
                R.id.navigation_messages -> true
                else -> false
            }
        }
    }
}