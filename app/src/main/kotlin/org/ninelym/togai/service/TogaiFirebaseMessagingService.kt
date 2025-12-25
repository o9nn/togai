package org.ninelym.togai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.ninelym.togai.R

/**
 * Firebase Cloud Messaging service
 * 
 * Handles push notifications and remote messages
 */
class TogaiFirebaseMessagingService : FirebaseMessagingService() {
    
    private val TAG = "TogaiFCM"
    private val CHANNEL_ID = "togai_notifications"
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM token: $token")
        // TODO: Send token to server
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.i(TAG, "Message received from: ${message.from}")
        
        message.notification?.let { notification ->
            showNotification(
                notification.title ?: "Togai",
                notification.body ?: "New message"
            )
        }
        
        message.data.let { data ->
            Log.i(TAG, "Message data: $data")
            // Handle data payload
        }
    }
    
    private fun showNotification(title: String, body: String) {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Togai Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General notifications from Togai"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
