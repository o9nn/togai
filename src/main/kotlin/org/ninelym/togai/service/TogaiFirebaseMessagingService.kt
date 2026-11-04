package org.ninelym.togai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.ninelym.togai.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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
        
        // Send token to server
        sendTokenToServer(token)
    }
    
    private fun sendTokenToServer(token: String) {
        val prefs = getSharedPreferences("togai_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // Registered with the push server configured under "push_server_url", if any
        val server = prefs.getString("push_server_url", null)
        if (server == null) {
            Log.i(TAG, "No push server configured; token kept on device only")
            return
        }
        Thread {
            try {
                val body = """{"token":"$token"}""".toRequestBody("application/json".toMediaType())
                okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder().url("${server.trimEnd('/')}/fcm-token").post(body).build()
                ).execute().use { response ->
                    if (response.isSuccessful) Log.i(TAG, "Token registered with push server")
                    else Log.e(TAG, "Push server rejected token: HTTP ${response.code}")
                }
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Failed to send token to push server", e)
            }
        }.start()
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.i(TAG, "Message received from: ${message.from}")
        
        message.notification?.let { notification ->
            // Only show what the sender actually sent
            val body = notification.body ?: return@let
            showNotification(
                notification.title ?: "Togai",
                body
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
