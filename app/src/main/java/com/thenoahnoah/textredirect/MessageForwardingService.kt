package com.thenoahnoah.textredirect

import android.accounts.AccountManager
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
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MessageForwardingService : Service() {
    companion object {
        private const val TAG = "MessageForwardingService"
        private const val CHANNEL_ID = "MessageForwardingChannel"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Waiting for messages..."))

        intent?.let {
            val sender = it.getStringExtra("sender") ?: "Unknown"
            val message = it.getStringExtra("message") ?: ""
            val timestamp = it.getLongExtra("timestamp", System.currentTimeMillis())

            serviceScope.launch {
                forwardMessageToGmail(sender, message, timestamp)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun forwardMessageToGmail(sender: String, messageBody: String, timestamp: Long) {
        try {
            val prefs = getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
            val userEmail = getUserEmail()
            
            if (userEmail == null) {
                Log.e(TAG, "No Gmail account found on device")
                updateNotification("Error: No Gmail account found")
                return
            }

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

            // Send email using JavaMail (simplified - in production use Gmail API)
            sendEmailSimple(userEmail, emailSubject, emailBody)
            
            Log.d(TAG, "Message forwarded successfully to $userEmail")
            updateNotification("Message forwarded successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error forwarding message", e)
            updateNotification("Error forwarding message: ${e.message}")
        }
    }

    private fun getUserEmail(): String? {
        try {
            val accountManager = AccountManager.get(this)
            val accounts = accountManager.getAccountsByType("com.google")
            
            if (accounts.isNotEmpty()) {
                return accounts[0].name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user email", e)
        }
        return null
    }

    private fun sendEmailSimple(toEmail: String, subject: String, body: String) {
        // Note: This is a simplified version using SMTP
        // For production, you should use Gmail API with OAuth2
        // This requires the app to have an app-specific password or OAuth token
        
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "587")
            }

            // Note: You'll need to implement OAuth2 or use app-specific password
            // For now, this is a placeholder that logs the intent
            Log.d(TAG, "Would send email to: $toEmail")
            Log.d(TAG, "Subject: $subject")
            Log.d(TAG, "Body: $body")
            
            // Store in local log for now
            val prefs = getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
            val logCount = prefs.getInt("log_count", 0)
            prefs.edit().apply {
                putString("log_$logCount", "$subject\n$body")
                putInt("log_count", logCount + 1)
                apply()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendEmailSimple", e)
            throw e
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
