package com.thenoahnoah.textredirect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageForwardingService : Service() {
    companion object {
        private const val TAG = "MessageForwardingService"
        private const val CHANNEL_ID = "MessageForwardingChannel"
        private const val NOTIFICATION_ID = 1
        
        // Deduplication cache - stores message hashes and timestamps
        private val recentMessages = mutableMapOf<String, Long>()
        private const val DEDUP_WINDOW_MS = 5000L // 5 seconds
        
        // Generate hash for deduplication
        private fun getMessageHash(sender: String, message: String, timestamp: Long): String {
            // Use timestamp within 5 second window to catch duplicates
            val roundedTimestamp = (timestamp / DEDUP_WINDOW_MS) * DEDUP_WINDOW_MS
            return "$sender:$message:$roundedTimestamp".hashCode().toString()
        }
        
        // Check if message was recently processed
        private fun isDuplicate(sender: String, message: String, timestamp: Long): Boolean {
            val hash = getMessageHash(sender, message, timestamp)
            val now = System.currentTimeMillis()
            
            // Clean up old entries (older than 30 seconds)
            recentMessages.entries.removeIf { now - it.value > 30000 }
            
            // Check if this message was recently processed
            if (recentMessages.containsKey(hash)) {
                Log.d(TAG, "Duplicate message detected, skipping")
                return true
            }
            
            // Mark as processed
            recentMessages[hash] = now
            return false
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var gmailHelper: GmailApiHelper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        gmailHelper = GmailApiHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Waiting for messages..."))

        intent?.let {
            val sender = it.getStringExtra("sender") ?: "Unknown"
            val message = it.getStringExtra("message") ?: ""
            val timestamp = it.getLongExtra("timestamp", System.currentTimeMillis())

            Log.d(TAG, "Processing message from $sender: $message")
            
            // Check for duplicate
            if (isDuplicate(sender, message, timestamp)) {
                AppLogger.d(TAG, "Skipping duplicate message")
                stopSelf(startId)
                return START_NOT_STICKY
            }

            AppLogger.i(TAG, "Processing message from $sender")
            serviceScope.launch {
                forwardMessageToGmail(sender, message, timestamp)
                stopSelf(startId)
            }
        } ?: run {
            Log.e(TAG, "Intent was null!")
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun forwardMessageToGmail(sender: String, messageBody: String, timestamp: Long) {
        try {
            Log.d(TAG, "forwardMessageToGmail started")
            
            if (!gmailHelper.isSignedIn()) {
                AppLogger.e(TAG, "User not signed in to Gmail")
                updateNotification("Error: Not signed in to Gmail")
                return
            }

            val userEmail = gmailHelper.getUserEmail()
            if (userEmail == null) {
                AppLogger.e(TAG, "Could not get user email")
                updateNotification("Error: Could not get email address")
                return
            }

            Log.d(TAG, "Sending to: $userEmail")
            updateNotification("Forwarding message from $sender...")

            // Format the message
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(timestamp))
            
            val emailSubject = "SMS from $sender"
            val emailBody = """
                SMS Message Received
                
                From: $sender
                Time: $formattedDate
                
                Message:
                $messageBody
            """.trimIndent()

            Log.d(TAG, "Calling sendEmail...")
            
            // Send email using Gmail API
            val result = gmailHelper.sendEmail(userEmail, emailSubject, emailBody)
            
            if (result.isSuccess) {
                AppLogger.i(TAG, "✓ Message forwarded successfully to $userEmail")
                updateNotification("Message forwarded successfully")
            } else {
                val error = result.exceptionOrNull()
                AppLogger.e(TAG, "Failed to forward message: ${error?.message}")
                updateNotification("Error: ${error?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding message", e)
            updateNotification("Error forwarding message: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Message Forwarding",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of message forwarding"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Text Redirect")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
