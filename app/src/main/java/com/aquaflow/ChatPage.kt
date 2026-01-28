package com.aquaflow

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val message: String,
    val time: String,
    val isSender: Boolean,
    val isDateSeparator: Boolean = false
)

class ChatPage : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_conversation)

        // Set Toolbar Names from Intent (Not hardcoded)
        val senderName = intent.getStringExtra("SENDER_NAME") ?: "Support"
        findViewById<TextView>(R.id.tvToolbarName).text = senderName

        chatContainer = findViewById(R.id.chatContainer)
        etMessage = findViewById(R.id.etMessageInput)
        btnSend = findViewById(R.id.btnSend)

        // Initial Data
        val messages = listOf(
            ChatMessage("Today, 10:15 AM", "", false, true),
            ChatMessage("Hi, I won't be home at 1 PM. Can we move the delivery to 2 PM instead?", "10:15 AM", true),
            ChatMessage("11:30 AM", "", false, true),
            ChatMessage("Sure! I've checked with the rider.", "11:30 AM", false),
            ChatMessage("Your refill schedule has been updated. The rider will arrive between 2:00 PM-2:30 PM.", "11:31 AM", false)
        )

        messages.forEach { addMessageToUI(it) }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString()
            if (text.isNotEmpty()) {
                val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                addMessageToUI(ChatMessage(text, currentTime, true))
                etMessage.text.clear()
            }
        }
    }

    private fun addMessageToUI(chat: ChatMessage) {
        val layoutId = when {
            chat.isDateSeparator -> R.layout.item_chat_date
            chat.isSender -> R.layout.item_chat_right
            else -> R.layout.item_chat_left
        }

        val view = layoutInflater.inflate(layoutId, chatContainer, false)

        if (chat.isDateSeparator) {
            view.findViewById<TextView>(R.id.tvChatDate).text = chat.message
        } else {
            val tvMessage = view.findViewById<TextView>(if (chat.isSender) R.id.tvMessageRight else R.id.tvMessageLeft)
            val tvTime = view.findViewById<TextView>(if (chat.isSender) R.id.tvTimeRight else R.id.tvTimeLeft)
            tvMessage.text = chat.message
            tvTime.text = chat.time
        }

        chatContainer.addView(view)
    }
}