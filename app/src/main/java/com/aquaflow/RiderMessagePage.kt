package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.ChatApi
import com.aquaflow.utils.ChatSocket
import com.aquaflow.utils.ConversationRow
import com.aquaflow.utils.RiderApi
import com.aquaflow.utils.RIDER_HEARTBEAT_INTERVAL_MS
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.socket.client.Socket
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RiderMessagePage : AppCompatActivity() {
    private lateinit var messagesContainer: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var inputSearch: TextInputEditText
    private lateinit var btnFilterAll: MaterialButton
    private lateinit var btnFilterStaff: MaterialButton
    private lateinit var btnFilterCustomers: MaterialButton
    private lateinit var btnToggleArchive: MaterialButton
    private lateinit var tvArchiveBanner: TextView
    private val allConversations = mutableListOf<ConversationRow>()
    private var activeFilter: String = "All"
    private var currentQuery: String = ""
    private val pageSize = 12
    private var visibleCount = pageSize
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val searchDebounceMs = 350L
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            heartbeatHandler.postDelayed(this, RIDER_HEARTBEAT_INTERVAL_MS)
        }
    }
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
        setContentView(R.layout.page_rider_message)

        messagesContainer = findViewById(R.id.messagesContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        inputSearch = findViewById(R.id.inputMessageSearch)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterStaff = findViewById(R.id.btnFilterStaff)
        btnFilterCustomers = findViewById(R.id.btnFilterCustomers)
        btnToggleArchive = findViewById(R.id.btnToggleArchive)
        tvArchiveBanner = findViewById(R.id.tvArchiveBanner)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.messagesScroll)
        scrollView?.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val view = scrollView.getChildAt(0)
            if (view != null && scrollY >= (view.measuredHeight - scrollView.measuredHeight - 12)) {
                loadMoreIfPossible()
            }
        }
        myUserId = getSharedPreferences("auth", MODE_PRIVATE).getString("userId", null)
        setupBottomNav()
        setupFilters()
        setupSocket()
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
        startHeartbeat()
    }

    override fun onPause() {
        super.onPause()
        stopHeartbeat()
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
        socket = null
        searchRunnable?.let(searchHandler::removeCallbacks)
    }

    private fun startHeartbeat() {
        sendHeartbeat()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        heartbeatHandler.postDelayed(heartbeatRunnable, RIDER_HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    private fun sendHeartbeat() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return
        RiderApi.sendHeartbeat(token) { }
    }

    private fun loadConversations() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            setLoading(false)
            return
        }

        ChatApi.listConversations(token, includeArchived = false, myUserId = myUserId) { result ->
            runOnUiThread {
                result.onSuccess { rows ->
                    allConversations.clear()
                    allConversations.addAll(rows)
                    resetVisibleCount()
                    applyFilters()
                    setLoading(false)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to load conversations", Toast.LENGTH_LONG).show()
                    setLoading(false)
                }
            }
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

    private fun renderMessages(data: List<ConversationRow>) {
        messagesContainer.removeAllViews()

        val visible = data.take(visibleCount)

        for (row in visible) {
            val itemView = layoutInflater.inflate(R.layout.item_message_row, messagesContainer, false)

            val tvSender = itemView.findViewById<TextView>(R.id.tvSenderName)
            val tvPreview = itemView.findViewById<TextView>(R.id.tvMessagePreview)
            val tvTime = itemView.findViewById<TextView>(R.id.tvMessageTime)
            val tvSenderInitial = itemView.findViewById<TextView>(R.id.tvSenderInitial)
            val tvOrderBadge = itemView.findViewById<TextView>(R.id.tvOrderBadge)
            val tvMeta = itemView.findViewById<TextView>(R.id.tvMetaLine)
            val dot = itemView.findViewById<View>(R.id.viewUnreadDot)

            val roleTag = formatRoleTag(row)
            tvSender.text = "$roleTag: ${row.counterpartyName.ifBlank { "Unknown" }}"
            tvPreview.text = row.lastMessage?.takeIf { it.isNotBlank() } ?: "No messages yet"
            tvTime.text = formatRecentTime(row.lastMessageAt)
            tvSenderInitial.text = buildInitials(row.counterpartyName)

            if (!row.orderCode.isNullOrBlank()) {
                tvOrderBadge.visibility = View.VISIBLE
                tvOrderBadge.text = "#${row.orderCode}"
            } else {
                tvOrderBadge.visibility = View.GONE
            }

            val statusText = row.orderStatus?.replace("_", " ") ?: "No order"
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

    private fun setupFilters() {
        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val nextQuery = s?.toString().orEmpty().trim()
                searchRunnable?.let(searchHandler::removeCallbacks)
                searchRunnable = Runnable {
                    currentQuery = nextQuery
                    resetVisibleCount()
                    applyFilters()
                }
                searchHandler.postDelayed(searchRunnable!!, searchDebounceMs)
            }
        })

        btnFilterAll.setOnClickListener { setFilter("All") }
        btnFilterStaff.setOnClickListener { setFilter("Staff") }
        btnFilterCustomers.setOnClickListener { setFilter("Customers") }

        btnToggleArchive.visibility = View.GONE
        tvArchiveBanner.visibility = View.GONE
        updateFilterStyles()
    }

    private fun setFilter(filter: String) {
        activeFilter = filter
        updateFilterStyles()
        resetVisibleCount()
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
        style(btnFilterCustomers, activeFilter == "Customers")
    }

    private fun applyFilters() {
        val query = currentQuery.lowercase()
        var base = allConversations.toList()

        if (activeFilter != "All") {
            base = base.filter {
                val role = it.counterpartyRole?.lowercase()
                when (activeFilter) {
                    "Staff" -> role == "staff"
                    "Customers" -> role == "customer"
                    else -> true
                }
            }
        }

        if (query.isNotBlank()) {
            base = base.filter {
                val name = it.counterpartyName.lowercase()
                val order = it.orderCode?.lowercase().orEmpty()
                val last = it.lastMessage?.lowercase().orEmpty()
                name.contains(query) || order.contains(query) || last.contains(query)
            }
        }

        base = sortConversations(base)
        renderMessages(base)
    }

    private fun resetVisibleCount() {
        visibleCount = pageSize
    }

    private fun loadMoreIfPossible() {
        val total = allConversations.size
        if (visibleCount >= total) return
        visibleCount = kotlin.math.min(visibleCount + pageSize, total)
        applyFilters()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_messages
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, RiderHomePage::class.java)); true
                }
                R.id.navigation_messages -> true
                R.id.navigation_orders -> {
                    startActivity(Intent(this, RiderOrderPage::class.java)); true
                }
                R.id.navigation_notifications -> {
                    startActivity(Intent(this, NotificationPage::class.java)); true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, RiderProfilePage::class.java)); true
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
            row.counterpartyRole.equals("customer", true) -> "Customer"
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

    private fun sortConversations(rows: List<ConversationRow>): List<ConversationRow> {
        return rows.sortedWith(
            compareBy<ConversationRow> { it.lastMessageAt.isNullOrBlank() }
                .thenByDescending { parseApiDate(it.lastMessageAt ?: "")?.time ?: Long.MIN_VALUE }
        )
    }

    private fun buildInitials(name: String?): String {
        val cleaned = name?.trim().orEmpty()
        if (cleaned.isBlank()) return "CF"
        val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "CF"
        val initials = parts.take(2).mapNotNull { part -> part.firstOrNull()?.uppercaseChar() }
        return initials.joinToString("").ifBlank { "CF" }
    }
}
