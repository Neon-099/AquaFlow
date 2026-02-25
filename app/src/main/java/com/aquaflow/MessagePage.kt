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
import com.aquaflow.utils.ChatSocket
import com.aquaflow.utils.ConversationRow
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.socket.client.Socket
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MessagePage : AppCompatActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var inputSearch: TextInputEditText
    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterStaff: MaterialButton
    private lateinit var btnFilterRiders: MaterialButton
    private lateinit var btnFilterCustomers: MaterialButton
    private lateinit var btnToggleArchive: MaterialButton
    private lateinit var tvArchiveBanner: TextView
    private val allConversations = mutableListOf<ConversationRow>()
    private var activeFilter: String = "All"
    private var showArchived: Boolean = false
    private var currentQuery: String = ""
    private var socket: Socket? = null
    private var myUserId: String? = null
    private val isoWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val isoNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_message)

        messagesContainer = findViewById(R.id.messagesContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        inputSearch = findViewById(R.id.inputMessageSearch)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterStaff = findViewById(R.id.btnFilterStaff)
        btnFilterRiders = findViewById(R.id.btnFilterRiders)
        btnFilterCustomers = findViewById(R.id.btnFilterCustomers)
        btnToggleArchive = findViewById(R.id.btnToggleArchive)
        tvArchiveBanner = findViewById(R.id.tvArchiveBanner)
        myUserId = getSharedPreferences("auth", MODE_PRIVATE).getString("userId", null)
        setupBottomNav()
        setupFilters()
        setupSocket()
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
        socket = null
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
        ChatApi.listConversations(token, includeArchived = showArchived, myUserId = myUserId) { result ->
            runOnUiThread {
                result.onSuccess { rows ->
                    allConversations.clear()
                    allConversations.addAll(rows)
                    applyFilters()
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
            tvSender.text = "$roleTag: ${row.counterpartyName.ifBlank { "Unknown" }}"
            tvPreview.text = row.lastMessage?.takeIf { it.isNotBlank() } ?: "No messages yet"
            tvTime.text = formatRecentTime(row.lastMessageAt)
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
                intent.putExtra("ARCHIVED_AT", row.archivedAt)
                startActivity(intent)
            }

            messagesContainer.addView(itemView)
        }
    }

    private fun setupSocket() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return
        socket = ChatSocket.connect(token).apply {
            on("chat:message") { args ->
                val incoming = args.firstOrNull() as? JSONObject ?: return@on
                val convId = incoming.optString("conversationId")
                val senderId = incoming.optString("senderId")
                if (convId.isBlank()) return@on
                runOnUiThread {
                    val updated = allConversations.map { row ->
                        if (row.id != convId) return@map row
                        row.copy(
                            lastMessage = incoming.optString("message", row.lastMessage),
                            lastMessageAt = incoming.optString("timestamp", row.lastMessageAt),
                            unreadCount = if (senderId.isNotBlank() && senderId == myUserId) row.unreadCount else row.unreadCount + 1
                        )
                    }.toMutableList()

                    val target = updated.find { it.id == convId }
                    if (target != null) {
                        updated.removeAll { it.id == convId }
                        updated.add(0, target)
                    }
                    allConversations.clear()
                    allConversations.addAll(updated)
                    applyFilters()
                }
            }
            on("chat:seen") { args ->
                val incoming = args.firstOrNull() as? JSONObject ?: return@on
                val convId = incoming.optString("conversationId")
                val userId = incoming.optString("userId")
                if (convId.isBlank() || userId.isBlank() || userId != myUserId) return@on
                runOnUiThread {
                    val updated = allConversations.map { row ->
                        if (row.id != convId) row else row.copy(unreadCount = 0)
                    }
                    allConversations.clear()
                    allConversations.addAll(updated)
                    applyFilters()
                }
            }
        }
    }

    private fun setupFilters() {
        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                currentQuery = s?.toString().orEmpty().trim()
                applyFilters()
            }
        })

        btnFilterAll.setOnClickListener { setFilter("All") }
        btnFilterStaff.setOnClickListener { setFilter("Staff") }
        btnFilterRiders.setOnClickListener { setFilter("Riders") }
        btnFilterCustomers.setOnClickListener { setFilter("Customers") }

        btnToggleArchive.setOnClickListener {
            showArchived = !showArchived
            btnToggleArchive.text = if (showArchived) "Show Inbox" else "Show Archived"
            tvArchiveBanner.visibility = if (showArchived) View.VISIBLE else View.GONE
            loadConversations()
        }

        tvArchiveBanner.visibility = if (showArchived) View.VISIBLE else View.GONE
        updateFilterStyles()
    }

    private fun setFilter(filter: String) {
        activeFilter = filter
        updateFilterStyles()
        applyFilters()
    }

    private fun updateFilterStyles() {
        val selectedColor = getColor(R.color.primary)
        val selectedText = getColor(R.color.text_white)
        val idleText = getColor(R.color.text_secondary)
        val idleStroke = getColor(R.color.border_light)

        fun style(btn: MaterialButton, isSelected: Boolean) {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (isSelected) selectedColor else android.graphics.Color.TRANSPARENT))
            btn.setTextColor(if (isSelected) selectedText else idleText)
            btn.strokeColor = android.content.res.ColorStateList.valueOf(idleStroke)
            btn.strokeWidth = if (isSelected) 0 else 1
        }

        style(btnFilterAll, activeFilter == "All")
        style(btnFilterStaff, activeFilter == "Staff")
        style(btnFilterRiders, activeFilter == "Riders")
        style(btnFilterCustomers, activeFilter == "Customers")
    }

    private fun applyFilters() {
        val query = currentQuery.lowercase()
        var base = allConversations.toList()

        if (!showArchived && activeFilter != "All") {
            base = base.filter {
                val role = it.counterpartyRole?.lowercase()
                when (activeFilter) {
                    "Staff" -> role == "staff"
                    "Riders" -> role == "rider"
                    "Customers" -> role == "customer"
                    else -> true
                }
            }
        }

        if (query.isNotBlank()) {
            base = base.filter {
                val name = it.counterpartyName.lowercase()
                val order = it.orderId?.lowercase().orEmpty()
                val last = it.lastMessage?.lowercase().orEmpty()
                name.contains(query) || order.contains(query) || last.contains(query)
            }
        }

        renderMessages(base)
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

    private fun formatRecentTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val parsed = parseApiDate(raw) ?: return ""
        val diffMs = System.currentTimeMillis() - parsed.time
        if (diffMs < 0) return "now"
        val minutes = diffMs / 60000
        val hours = diffMs / 3600000
        val days = diffMs / 86400000

        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            else -> SimpleDateFormat("MMM d", Locale.US).format(parsed)
        }
    }

    private fun parseApiDate(raw: String): Date? {
        return try {
            isoWithMillis.parse(raw)
        } catch (_: Exception) {
            try {
                isoNoMillis.parse(raw)
            } catch (_: Exception) {
                null
            }
        }
    }
}
