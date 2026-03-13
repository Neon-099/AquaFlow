package com.aquaflow.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aquaflow.ChatPage
import com.aquaflow.HomePage
import com.aquaflow.OrderTrackingPage
import com.aquaflow.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AquaFlowFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "AquaFlow"
        val body = remoteMessage.notification?.body ?: "You have a new update"

        val conversationId = remoteMessage.data["conversationId"].orEmpty()
        val orderId = remoteMessage.data["orderId"].orEmpty()
        val type = remoteMessage.data["type"].orEmpty()

        val targetIntent = if (type == "order_status" && orderId.isNotBlank()) {
            Intent(this, OrderTrackingPage::class.java).apply {
                putExtra("order_id", orderId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else if (conversationId.isNotBlank()) {
            Intent(this, ChatPage::class.java).apply {
                putExtra("CONVERSATION_ID", conversationId)
                putExtra("ORDER_ID", orderId.ifBlank { null })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(this, HomePage::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "aquaflow_general"
        createChannelIfNeeded(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.aq_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationManagerCompat.from(this).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    private fun createChannelIfNeeded(channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            "AquaFlow Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }
}
