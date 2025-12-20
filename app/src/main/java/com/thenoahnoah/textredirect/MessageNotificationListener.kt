package com.thenoahnoah.textredirect

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MessageNotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "MessageNotifListener"
        private const val MESSAGES_PACKAGE = "com.google.android.apps.messaging"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        // Check if service is enabled
        val prefs = getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean("service_enabled", false)
        
        if (!isServiceEnabled) {
            AppLogger.d(TAG, "Service disabled, ignoring notification")
            return
        }
        
        // Only process notifications from Google Messages app
        if (sbn.packageName != MESSAGES_PACKAGE) {
            return
        }
        
        AppLogger.i(TAG, "New message notification received")
        
        val notification = sbn.notification
        val extras = notification.extras
        
        // Extract message details from notification
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text
        
        AppLogger.d(TAG, "From: $title")
        AppLogger.d(TAG, "Message: ${bigText.take(50)}${if (bigText.length > 50) "..." else ""}")
        
        // If we have both sender and message content, forward it
        if (title.isNotEmpty() && bigText.isNotEmpty()) {
            AppLogger.i(TAG, "Forwarding message from $title")
            
            // Start the forwarding service
            val intent = Intent(this, MessageForwardingService::class.java).apply {
                putExtra("sender", title)
                putExtra("message", bigText)
                putExtra("timestamp", System.currentTimeMillis())
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
    }
}
