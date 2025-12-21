package com.thenoahnoah.textredirect

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
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
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        
        // Detect message type based on notification content
        val messageType = when {
            subText.contains("RCS", ignoreCase = true) -> "RCS"
            subText.contains("MMS", ignoreCase = true) || bigText.contains("Download") -> "MMS"
            notification.category == Notification.CATEGORY_MESSAGE -> {
                // Check for RCS indicators in the notification
                if (notification.extras.containsKey("android.isGroupConversation") || 
                    subText.isNotEmpty()) "RCS" else "SMS"
            }
            else -> "SMS"
        }
        
        AppLogger.d(TAG, "From: $title, Type: $messageType")
        AppLogger.d(TAG, "Message: ${bigText.take(50)}${if (bigText.length > 50) "..." else ""}")
        
        // Get contact name for logging
        val contactName = getContactName(title)
        val displayName = if (contactName != null) "$contactName ($title)" else title
        
        // If we have both sender and message content, forward it
        if (title.isNotEmpty() && bigText.isNotEmpty()) {
            AppLogger.i(TAG, "Forwarding $messageType message from $displayName")
            
            // Start the forwarding service
            val intent = Intent(this, MessageForwardingService::class.java).apply {
                putExtra("sender", title)
                putExtra("message", bigText)
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("messageType", messageType)
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
}
