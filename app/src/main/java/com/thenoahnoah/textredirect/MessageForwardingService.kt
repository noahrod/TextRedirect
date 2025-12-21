package com.thenoahnoah.textredirect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
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
            val messageType = it.getStringExtra("messageType") ?: "SMS"

            Log.d(TAG, "Processing message from $sender: $message")
            
            // Check for duplicate
            if (isDuplicate(sender, message, timestamp)) {
                AppLogger.d(TAG, "Skipping duplicate message")
                stopSelf(startId)
                return START_NOT_STICKY
            }

            AppLogger.i(TAG, "Processing message from $sender")
            serviceScope.launch {
                forwardMessageToGmail(sender, message, timestamp, messageType)
                stopSelf(startId)
            }
        } ?: run {
            Log.e(TAG, "Intent was null!")
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun forwardMessageToGmail(sender: String, messageBody: String, timestamp: Long, messageType: String) {
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

            // Get contact name if available
            val contactName = getContactName(sender)
            val displayName = if (contactName != null) {
                "$contactName ($sender)"
            } else {
                sender
            }

            // Format the message
            val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm:ss a", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(timestamp))
            
            val emailSubject = "[$messageType] Message from ${contactName ?: sender}"
            val emailBody = createHtmlEmail(displayName, messageBody, formattedDate, messageType)

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
    
    private fun getContactName(phoneNumber: String): String? {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
        }
        return null
    }
    
    private fun createHtmlEmail(sender: String, message: String, date: String, messageType: String): String {
        val typeColor = when (messageType) {
            "RCS" -> "#1E88E5"
            "MMS" -> "#43A047"
            else -> "#757575" // SMS
        }
        
        val typeIcon = when (messageType) {
            "RCS" -> "💬"
            "MMS" -> "📷"
            else -> "📱" // SMS
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f5f5f5;">
                <table width="100%" cellpadding="0" cellspacing="0" style="background-color: #f5f5f5; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); overflow: hidden;">
                                <!-- Header -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 600;">
                                            $typeIcon New $messageType Message
                                        </h1>
                                    </td>
                                </tr>
                                
                                <!-- Message Type Badge -->
                                <tr>
                                    <td style="padding: 20px 40px 0;">
                                        <div style="display: inline-block; background-color: $typeColor; color: #ffffff; padding: 6px 12px; border-radius: 4px; font-size: 12px; font-weight: 600; text-transform: uppercase;">
                                            $messageType
                                        </div>
                                    </td>
                                </tr>
                                
                                <!-- Sender Info -->
                                <tr>
                                    <td style="padding: 20px 40px;">
                                        <table width="100%" cellpadding="0" cellspacing="0">
                                            <tr>
                                                <td style="padding: 15px; background-color: #f8f9fa; border-left: 4px solid $typeColor; border-radius: 4px;">
                                                    <div style="color: #6c757d; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 5px;">
                                                        From
                                                    </div>
                                                    <div style="color: #212529; font-size: 18px; font-weight: 600;">
                                                        ${sender.replace("<", "&lt;").replace(">", "&gt;")}
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                
                                <!-- Message Content -->
                                <tr>
                                    <td style="padding: 0 40px 20px;">
                                        <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; border: 1px solid #e9ecef;">
                                            <div style="color: #6c757d; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px;">
                                                Message
                                            </div>
                                            <div style="color: #212529; font-size: 16px; line-height: 1.6; white-space: pre-wrap; word-wrap: break-word;">
                                                ${message.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")}
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                                
                                <!-- Timestamp -->
                                <tr>
                                    <td style="padding: 0 40px 30px;">
                                        <div style="color: #6c757d; font-size: 13px; text-align: center;">
                                            <span style="display: inline-block; padding: 8px 16px; background-color: #f8f9fa; border-radius: 20px;">
                                                📅 $date
                                            </span>
                                        </div>
                                    </td>
                                </tr>
                                
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8f9fa; padding: 20px 40px; text-align: center; border-top: 1px solid #e9ecef;">
                                        <p style="margin: 0; color: #6c757d; font-size: 12px;">
                                            Forwarded by <strong>TextRedirect</strong>
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
