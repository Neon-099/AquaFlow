package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.ChatApi
import com.aquaflow.utils.ConversationRow
import com.google.android.material.bottomnavigation.BottomNavigationView

class MessagePage : AppCompatActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_message)

        messagesContainer = findViewById(R.id.messagesContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        setupBottomNav()
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun loadConversations() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            setLoading(false)
            return
        }

        // includeArchived=false -> completed/cancelled order chats are automatically hidden (archived)
        ChatApi.listConversations(token, includeArchived = false) { result ->
            runOnUiThread {
                result.onSuccess { rows ->
                    renderMessages(rows)
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to load conversations", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
        }
    }

    private fun renderMessages(data: List<ConversationRow>) {
        messagesContainer.removeAllViews()

        for (row in data) {
            val itemView = layoutInflater.inflate(R.layout.item_message_row, messagesContainer, false)

            val tvSender = itemView.findViewById<TextView>(R.id.tvSenderName)
            val tvPreview = itemView.findViewById<TextView>(R.id.tvMessagePreview)
            val tvTime = itemView.findViewById<TextView>(R.id.tvMessageTime)
            val ivProfile = itemView.findViewById<ImageView>(R.id.ivSenderProfile)
            val tvOrderBadge = itemView.findViewById<TextView>(R.id.tvOrderBadge)
            val tvMeta = itemView.findViewById<TextView>(R.id.tvMetaLine)
            val dot = itemView.findViewById<View>(R.id.viewUnreadDot)

            val roleTag = formatRoleTag(row)
            tvSender.text = "$roleTag: ${row.counterpartyName}"
            tvPreview.text = row.lastMessage ?: "No messages yet"
            tvTime.text = row.lastMessageAt ?: ""
            ivProfile.setImageResource(R.drawable.ic_profile)

            if (!row.orderId.isNullOrBlank()) {
                tvOrderBadge.visibility = View.VISIBLE
                tvOrderBadge.text = "Order #${row.orderId.takeLast(6)}"
            } else {
                tvOrderBadge.visibility = View.GONE
            }

            val statusText = row.orderStatus?.replace("_", " ") ?: "No order"
            val labelText = roleTag
            tvMeta.text = statusText

            dot.visibility = if (row.unreadCount > 0) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener {
                val intent = Intent(this, ChatPage::class.java)
                intent.putExtra("CONVERSATION_ID", row.id)
                intent.putExtra("SENDER_NAME", row.counterpartyName)
                intent.putExtra("ORDER_ID", row.orderId)
                intent.putExtra("ORDER_STATUS", row.orderStatus)
                intent.putExtra("COUNTERPARTY_LABEL", row.counterpartyLabel)
                startActivity(intent)
            }

            messagesContainer.addView(itemView)
        }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_messages
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_messages -> true
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomePage::class.java)); true
                }
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java)); true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfilePage::class.java)); true
                }
                else -> false
            }
        }
    }

    private fun formatRoleTag(row: ConversationRow): String {
        val label = row.counterpartyLabel?.trim()?.lowercase()
        return when {
            label == "assigned rider" -> "Assigned Rider"
            label == "rider" -> "Unassigned Rider"
            label == "staff support" || row.counterpartyRole.equals("staff", true) -> "Staff"
            row.counterpartyRole.equals("rider", true) -> "Unassigned Rider"
            else -> "Chat"
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}
