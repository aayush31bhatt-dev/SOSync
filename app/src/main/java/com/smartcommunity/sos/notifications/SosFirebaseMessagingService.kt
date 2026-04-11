package com.smartcommunity.sos.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SosFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        ensureChannel()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Nearby SOS request"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Someone nearby needs help."

        val notification = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
            .notify((message.messageId ?: body).hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        // Token registration is handled by dashboard heartbeat/register flow.
        super.onNewToken(token)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            SOS_CHANNEL_ID,
            "SOS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val SOS_CHANNEL_ID = "sos_alerts"
    }
}
