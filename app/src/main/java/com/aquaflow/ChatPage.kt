package com.aquaflow

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aquaflow.utils.ChatSocket
import com.aquaflow.utils.ChatApi
import com.aquaflow.utils.ConversationMessage
import com.aquaflow.utils.OrderApi
import io.socket.client.Socket
import org.json.JSONObject

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
    private lateinit var tvTypingIndicator: TextView
    private lateinit var tvArchivedBanner: TextView

    private var conversationId: String = ""
    private var orderId: String? = null
    private var myUserId: String = ""
    private var isArchived: Boolean = false
    private var socket: Socket? = null
    private val typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var stopTypingRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_conversation)

        conversationId = intent.getStringExtra("CONVERSATION_ID").orEmpty()
        orderId = intent.getStringExtra("ORDER_ID")
        myUserId = getSharedPreferences("auth", MODE_PRIVATE).getString("userId", "").orEmpty()
        isArchived = !intent.getStringExtra("ARCHIVED_AT").isNullOrBlank()

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
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator)
        tvArchivedBanner = findViewById(R.id.tvArchivedBanner)

        val senderName = intent.getStringExtra("SENDER_NAME").orEmpty()
        val counterpartyLabel = intent.getStringExtra("COUNTERPARTY_LABEL").orEmpty()
        tvToolbarName.text = buildToolbarTitle(senderName, counterpartyLabel)
        tvStatusSubtitle.text = counterpartyLabel.ifBlank { "Conversation" }

        btnBack.setOnClickListener { finish() }

        renderOrderContext()
        renderArchivedState()
        loadMessages()
        setupSocket()
        setupTypingListener()

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty() || conversationId.isBlank() || isArchived) return@setOnClickListener
            sendMessage(message)
        }
    }

    override fun onResume() {
        super.onResume()
        markSeen()
    }

    override fun onDestroy() {
        super.onDestroy()
        typingHandler.removeCallbacksAndMessages(null)
        socket?.disconnect()
        socket = null
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

    private fun renderArchivedState() {
        tvArchivedBanner.visibility = if (isArchived) View.VISIBLE else View.GONE
        if (isArchived) {
            etMessage.isEnabled = false
            btnSend.isEnabled = false
            etMessage.hint = "Archived conversations are read-only."
        } else {
            etMessage.isEnabled = true
            btnSend.isEnabled = true
        }
    }

    private fun setupSocket() {
        val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
        if (token.isNullOrBlank() || conversationId.isBlank()) return

        socket = ChatSocket.connect(token).apply {
            on(Socket.EVENT_CONNECT) {
                emit("chat:join", JSONObject().put("conversationId", conversationId))
            }
            on("chat:message") { args ->
                val incoming = args.firstOrNull() as? JSONObject ?: return@on
                val convId = incoming.optString("conversationId")
                if (convId != conversationId) return@on
                runOnUiThread {
                    addMessageToUI(
                        ConversationMessage(
                            id = incoming.optString("_id"),
                            message = incoming.optString("message"),
                            senderId = incoming.optString("senderId"),
                            receiverId = incoming.optString("receiverId"),
                            timestamp = incoming.optString("timestamp"),
                            seenAt = incoming.optString("seenAt")
                        )
                    )
                    markSeen()
                }
            }
            on("chat:typing") { args ->
                val incoming = args.firstOrNull() as? JSONObject ?: return@on
                val convId = incoming.optString("conversationId")
                if (convId != conversationId) return@on
                val isTyping = incoming.optBoolean("isTyping", false)
                runOnUiThread {
                    tvTypingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupTypingListener() {
        if (isArchived) return
        etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                emitTyping(true)
                stopTypingRunnable?.let { typingHandler.removeCallbacks(it) }
                stopTypingRunnable = Runnable { emitTyping(false) }
                typingHandler.postDelayed(stopTypingRunnable!!, 900)
            }
        })
    }

    private fun emitTyping(isTyping: Boolean) {
        val payload = JSONObject().put("conversationId", conversationId).put("isTyping", isTyping)
        socket?.emit("chat:typing", payload)
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
        if (token.isNullOrBlank() || isArchived) return

        if (socket?.connected() == true) {
            val payload = JSONObject().put("conversationId", conversationId).put("message", message)
            ChatSocket.emitWithAck(
                socket = socket!!,
                event = "chat:message",
                payload = payload,
                onSuccess = { data ->
                    runOnUiThread {
                        etMessage.text.clear()
                        addMessageToUI(
                            ConversationMessage(
                                id = data.optString("_id"),
                                message = data.optString("message"),
                                senderId = data.optString("senderId"),
                                receiverId = data.optString("receiverId"),
                                timestamp = data.optString("timestamp"),
                                seenAt = data.optString("seenAt")
                            )
                        )
                        markSeen()
                    }
                },
                onError = {
                    fallbackSend(token, message)
                }
            )
        } else {
            fallbackSend(token, message)
        }
    }

    private fun fallbackSend(token: String, message: String) {
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
