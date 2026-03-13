package com.aquaflow

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.NotificationApi
import com.aquaflow.utils.NotificationRow
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationPage : AppCompatActivity() {
    private lateinit var notificationsContainer: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var btnMarkAllRead: MaterialButton

    private val orderNotifications = mutableListOf<NotificationRow>()
    private val messageNotifications = mutableListOf<NotificationRow>()

    private val isoWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val isoNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_notifications)

        notificationsContainer = findViewById(R.id.notificationsContainer)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead)

        setupBottomNav()
        setupActions()
        loadNotifications()
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
        updateMessageBadge()
        updateNotificationBadge()
    }

    private fun setupActions() {
        btnMarkAllRead.setOnClickListener { markAllRead() }
    }

    private fun loadNotifications() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        val role = getSharedPreferences("auth", MODE_PRIVATE).getString("role", null)?.lowercase()
        if (token.isNullOrBlank()) {
            Toast.makeText(this, "Missing session. Please log in again.", Toast.LENGTH_LONG).show()
            setLoading(false)
            return
        }

        if (role == "rider") {
            NotificationApi.listMessageNotifications(token) { messageResult ->
                runOnUiThread {
                    messageResult.onSuccess {
                        messageNotifications.clear()
                        messageNotifications.addAll(it)
                        orderNotifications.clear()
                    }.onFailure {
                        Toast.makeText(this, it.message ?: "Unable to load message notifications", Toast.LENGTH_LONG).show()
                    }
                    renderNotifications()
                    setLoading(false)
                }
            }
            return
        }

        NotificationApi.listOrderNotifications(token) { orderResult ->
            NotificationApi.listMessageNotifications(token) { messageResult ->
                runOnUiThread {
                    orderResult.onSuccess {
                        orderNotifications.clear()
                        orderNotifications.addAll(it)
                    }.onFailure {
                        Toast.makeText(this, it.message ?: "Unable to load order notifications", Toast.LENGTH_LONG).show()
                    }

                    messageResult.onSuccess {
                        messageNotifications.clear()
                        messageNotifications.addAll(it)
                    }.onFailure {
                        Toast.makeText(this, it.message ?: "Unable to load message notifications", Toast.LENGTH_LONG).show()
                    }

                    renderNotifications()
                    setLoading(false)
                }
            }
        }
    }

    private fun renderNotifications() {
        notificationsContainer.removeAllViews()
        val data = (orderNotifications + messageNotifications)
            .sortedByDescending { parseApiDate(it.createdAt)?.time ?: 0L }
        if (data.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No notifications yet."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(20, 24, 20, 8)
            }
            notificationsContainer.addView(empty)
            return
        }

        data.forEach { row ->
            val itemView = layoutInflater.inflate(R.layout.item_notification_row, notificationsContainer, false)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvNotifTitle)
            val tvMessage = itemView.findViewById<TextView>(R.id.tvNotifMessage)
            val tvTime = itemView.findViewById<TextView>(R.id.tvNotifTime)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvNotifBadge)
            val dot = itemView.findViewById<View>(R.id.viewUnreadDot)
            val btnMark = itemView.findViewById<MaterialButton>(R.id.btnMarkRead)

            tvTitle.text = row.title.ifBlank { "Notification" }
            tvMessage.text = row.message.ifBlank { "You have a new update." }
            tvTime.text = formatRecentTime(row.createdAt)
            dot.visibility = if (row.isRead) View.INVISIBLE else View.VISIBLE

            if (row.type == "message") {
                tvBadge.text = "MESSAGE"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_blue_solid)
            } else {
                tvBadge.text = "ORDER"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_green)
            }

            if (row.isRead) {
                btnMark.text = "Read"
                btnMark.isEnabled = false
                btnMark.setTextColor(getColor(R.color.text_secondary))
                btnMark.strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.border_light))
            } else {
                btnMark.text = "Mark read"
                btnMark.isEnabled = true
                btnMark.setOnClickListener { markOneRead(row) }
            }

            notificationsContainer.addView(itemView)
        }
    }

    private fun markOneRead(row: NotificationRow) {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null) ?: return
        val markFn = if (row.type == "message") NotificationApi::markMessageRead else NotificationApi::markOrderRead
        markFn.invoke(token, listOf(row.id)) { result ->
            runOnUiThread {
                result.onSuccess {
                    updateReadState(row.id)
                }.onFailure {
                    Toast.makeText(this, it.message ?: "Unable to mark as read", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun markAllRead() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null) ?: return
        val role = getSharedPreferences("auth", MODE_PRIVATE).getString("role", null)?.lowercase()
        if (role == "rider") {
            NotificationApi.markMessageRead(token, emptyList()) { messageResult ->
                runOnUiThread {
                    if (messageResult.isFailure) {
                        Toast.makeText(this, "Unable to update notifications", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    markAllLocal(messageNotifications)
                }
            }
            return
        }

        NotificationApi.markOrderRead(token, emptyList()) { orderResult ->
            NotificationApi.markMessageRead(token, emptyList()) { messageResult ->
                runOnUiThread {
                    if (orderResult.isFailure || messageResult.isFailure) {
                        Toast.makeText(this, "Unable to update notifications", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    markAllLocal(orderNotifications)
                    markAllLocal(messageNotifications)
                }
            }
        }
    }

    private fun markAllLocal(list: MutableList<NotificationRow>) {
        for (i in list.indices) {
            list[i] = list[i].copy(isRead = true)
        }
        renderNotifications()
    }

    private fun updateReadState(id: String) {
        fun update(list: MutableList<NotificationRow>) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(isRead = true)
        }
        update(orderNotifications)
        update(messageNotifications)
        renderNotifications()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.navigation_notifications
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_notifications -> true
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomePage::class.java)); true
                }
                R.id.navigation_orders -> {
                    startActivity(Intent(this, OrderPage::class.java)); true
                }
                R.id.navigation_messages -> {
                    startActivity(Intent(this, MessagePage::class.java)); true
                }
                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfilePage::class.java)); true
                }
                else -> false
            }
        }
    }

    private fun updateMessageBadge() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null) ?: return
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        NotificationApi.getUnreadMessageCount(token) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    val badge = bottomNav.getOrCreateBadge(R.id.navigation_messages)
                    badge.isVisible = count > 0
                    badge.number = count.coerceAtMost(99)
                }
            }
        }
    }

    private fun updateNotificationBadge() {
        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return
        val role = prefs.getString("role", null)?.lowercase()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        if (role == "rider") {
            NotificationApi.getUnreadMessageCount(token) { messageResult ->
                runOnUiThread {
                    val total = messageResult.getOrNull() ?: 0
                    val badge = bottomNav.getOrCreateBadge(R.id.navigation_notifications)
                    badge.isVisible = total > 0
                    badge.number = total.coerceAtMost(99)
                }
            }
            return
        }

        NotificationApi.getUnreadOrderCount(token) { orderResult ->
            NotificationApi.getUnreadMessageCount(token) { messageResult ->
                runOnUiThread {
                    val orderCount = orderResult.getOrNull() ?: 0
                    val messageCount = messageResult.getOrNull() ?: 0
                    val total = orderCount + messageCount
                    val badge = bottomNav.getOrCreateBadge(R.id.navigation_notifications)
                    badge.isVisible = total > 0
                    badge.number = total.coerceAtMost(99)
                }
            }
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

    private fun parseApiDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
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
