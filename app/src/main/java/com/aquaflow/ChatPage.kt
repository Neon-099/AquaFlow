package com.aquaflow

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.ChatApi
import com.aquaflow.utils.ConversationMessage
import com.aquaflow.utils.OrderApi

class ChatPage : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvToolbarName: TextView
    private lateinit var tvStatusSubtitle: TextView
    private lateinit var orderContextCard: View
    private lateinit var tvOrderContextTitle: TextView
    private lateinit var tvOrderContextStatus: TextView
    private lateinit var loadingOverlay: View

    private var conversationId: String = ""
    private var orderId: String? = null
    private var myUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_conversation)

        conversationId = intent.getStringExtra("CONVERSATION_ID").orEmpty()
        orderId = intent.getStringExtra("ORDER_ID")
        myUserId = getSharedPreferences("auth", MODE_PRIVATE).getString("userId", "").orEmpty()

        tvToolbarName = findViewById(R.id.tvToolbarName)
        tvStatusSubtitle = findViewById(R.id.tvStatusSubtitle)
        chatContainer = findViewById(R.id.chatContainer)
        etMessage = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)

        orderContextCard = findViewById(R.id.orderContextCard)
        tvOrderContextTitle = findViewById(R.id.tvOrderContextTitle)
        tvOrderContextStatus = findViewById(R.id.tvOrderContextStatus)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        val senderName = intent.getStringExtra("SENDER_NAME").orEmpty()
        val counterpartyLabel = intent.getStringExtra("COUNTERPARTY_LABEL").orEmpty()
        tvToolbarName.text = buildToolbarTitle(senderName, counterpartyLabel)
        tvStatusSubtitle.text = counterpartyLabel.ifBlank { "Conversation" }

        btnBack.setOnClickListener { finish() }

        renderOrderContext()
        loadMessages()

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty() || conversationId.isBlank()) return@setOnClickListener
            sendMessage(message)
        }
    }

    override fun onResume() {
        super.onResume()
        markSeen()
    }

    private fun renderOrderContext() {
        val oid = orderId
        if (oid.isNullOrBlank()) {
            orderContextCard.visibility = View.GONE
            return
        }

        orderContextCard.visibility = View.VISIBLE
        tvOrderContextTitle.text = "Order #${oid.takeLast(6)}"

        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) {
            tvOrderContextStatus.text = "Order context unavailable"
            return
        }

        OrderApi.getOrderById(token, oid) { result ->
            runOnUiThread {
                result.onSuccess { details ->
                    val code = details.order.orderCode ?: oid.takeLast(6)
                    tvOrderContextTitle.text = "Order #$code"
                    tvOrderContextStatus.text = details.order.status.replace("_", " ")
                }.onFailure {
                    tvOrderContextStatus.text = (intent.getStringExtra("ORDER_STATUS")
                        ?: "Status unavailable").replace("_", " ")
                }
            }
        }
    }

    private fun loadMessages() {
        setLoading(true)
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank() || conversationId.isBlank()) {
            setLoading(false)
            return
        }

        ChatApi.getMessages(token, conversationId) { result ->
            runOnUiThread {
                result.onSuccess { messages ->
                    chatContainer.removeAllViews()
                    messages.forEach { addMessageToUI(it) }
                    markSeen()
                    setLoading(false)
                }.onFailure {
                    setLoading(false)
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank()) return

        ChatApi.sendMessage(token, conversationId, message) { result ->
            runOnUiThread {
                result.onSuccess { saved ->
                    etMessage.text.clear()
                    addMessageToUI(saved)
                    markSeen()
                }.onFailure {
                    // keep silent for now
                }
            }
        }
    }

    private fun markSeen() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank() || conversationId.isBlank()) return
        ChatApi.markSeen(token, conversationId) { }
    }

    private fun addMessageToUI(chat: ConversationMessage) {
        val isSender = chat.senderId == myUserId
        val layoutId = if (isSender) R.layout.item_chat_right else R.layout.item_chat_left
        val view = layoutInflater.inflate(layoutId, chatContainer, false)

        if (isSender) {
            view.findViewById<TextView>(R.id.tvMessageRight).text = chat.message
            view.findViewById<TextView>(R.id.tvTimeRight).text = chat.timestamp ?: ""
        } else {
            view.findViewById<TextView>(R.id.tvMessageLeft).text = chat.message
            view.findViewById<TextView>(R.id.tvTimeLeft).text = chat.timestamp ?: ""
        }

        chatContainer.addView(view)
    }

    private fun setLoading(isLoading: Boolean) {
        loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun buildToolbarTitle(senderName: String, counterpartyLabel: String): String {
        val cleanName = senderName.trim().ifBlank { "Chat" }
        val cleanLabel = counterpartyLabel.trim()
        if (cleanLabel.contains("rider", ignoreCase = true) && cleanName != "Chat") {
            return "$cleanLabel: $cleanName"
        }
        return cleanName
    }
}
