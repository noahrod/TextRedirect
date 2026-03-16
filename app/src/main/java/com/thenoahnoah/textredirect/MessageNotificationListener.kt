package com.thenoahnoah.textredirect

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class MessageNotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "MessageNotifListener"
        private const val MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val DEBOUNCE_DELAY_MS = 5000L // Wait 1.5 seconds for notification updates
    }
    
    // Handler for debouncing notifications
    private val handler = Handler(Looper.getMainLooper())
    
    // Pending messages waiting to be forwarded (keyed by sender)
    private val pendingMessages = mutableMapOf<String, PendingMessage>()
    
    private data class PendingMessage(
        val sender: String,
        val message: String,
        val timestamp: Long,
        val messageType: String,
        val imagePath: String?,
        val runnable: Runnable
    )
    
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
        
        // Debug: Log all notification extras to understand what Google Messages sends
        AppLogger.d(TAG, "=== Notification Debug Info ===")
        AppLogger.d(TAG, "Title: $title")
        AppLogger.d(TAG, "Text: $text")
        AppLogger.d(TAG, "BigText: $bigText")
        AppLogger.d(TAG, "SubText: '$subText'")
        AppLogger.d(TAG, "Category: ${notification.category}")
        
        // Log all extra keys for debugging
        extras.keySet().forEach { key ->
            val value = extras.get(key)
            AppLogger.d(TAG, "Extra[$key]: ${value?.javaClass?.simpleName} = ${value?.toString()?.take(100)}")
        }
        
        // Detect message type based on notification content
        // Google Messages RCS typically uses MessagingStyle and may have specific indicators
        val isMessagingStyle = extras.containsKey(Notification.EXTRA_MESSAGES)
        val hasConversationTitle = extras.containsKey(Notification.EXTRA_CONVERSATION_TITLE)
        val isGroupConversation = extras.getBoolean("android.isGroupConversation", false)
        
        // Check for RCS-specific extras from Google Messages
        val hasRcsMessageId = extras.containsKey("extra_im_notification_latest_rcs_message_id")
        val subTextLower = subText.lowercase()
        // "Helium" is Google's internal RCS backend codename
        val isHeliumRcs = subTextLower == "helium" || subTextLower.contains("rcs") || subTextLower.contains("chat")
        
        AppLogger.d(TAG, "isMessagingStyle: $isMessagingStyle, hasConversationTitle: $hasConversationTitle, isGroupConversation: $isGroupConversation")
        AppLogger.d(TAG, "hasRcsMessageId: $hasRcsMessageId, isHeliumRcs: $isHeliumRcs")
        
        val messageType = when {
            // RCS indicators from Google Messages
            hasRcsMessageId -> "RCS"
            isHeliumRcs -> "RCS"
            // Explicit indicators in text
            subText.contains("RCS", ignoreCase = true) || 
                subText.contains("Chat", ignoreCase = true) ||
                text.contains("RCS", ignoreCase = true) -> "RCS"
            subText.contains("MMS", ignoreCase = true) || 
                bigText.contains("Download", ignoreCase = true) ||
                bigText.contains("MMS", ignoreCase = true) ||
                text.contains("MMS", ignoreCase = true) -> "MMS"
            // MessagingStyle with image attachments is likely MMS
            isMessagingStyle && hasImageAttachment(extras) -> "MMS"
            // Default to SMS
            else -> "SMS"
        }
        
        AppLogger.d(TAG, "Detected message type: $messageType")
        
        // Get contact name for logging
        val contactName = getContactName(title)
        val displayName = if (contactName != null) "$contactName ($title)" else title
        
        // If we have both sender and message content, schedule forwarding with debounce
        if (title.isNotEmpty() && bigText.isNotEmpty()) {
            // Try to extract image from notification
            val imagePath = extractAndSaveImage(extras, timestamp = System.currentTimeMillis())
            if (imagePath != null) {
                AppLogger.d(TAG, "Image extracted and saved to: $imagePath")
            }
            
            // Schedule forwarding with debounce (wait for notification updates)
            scheduleForwarding(title, bigText, messageType, imagePath)
        }
    }
    
    private fun scheduleForwarding(sender: String, message: String, messageType: String, imagePath: String?) {
        // Cancel any pending forward for this sender
        pendingMessages[sender]?.let { pending ->
            handler.removeCallbacks(pending.runnable)
            // Clean up old image if we have a new one
            if (pending.imagePath != null && imagePath != null && pending.imagePath != imagePath) {
                try {
                    File(pending.imagePath).delete()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            AppLogger.d(TAG, "Cancelled pending forward for $sender, will use updated notification")
        }
        
        val timestamp = System.currentTimeMillis()
        
        // Create runnable for delayed forwarding
        val forwardRunnable = Runnable {
            AppLogger.i(TAG, "Debounce complete, forwarding $messageType message from $sender")
            pendingMessages.remove(sender)
            
            // Start the forwarding service
            val intent = Intent(this, MessageForwardingService::class.java).apply {
                putExtra("sender", sender)
                putExtra("message", message)
                putExtra("timestamp", timestamp)
                putExtra("messageType", messageType)
                putExtra("imagePath", imagePath)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        // Store and schedule
        pendingMessages[sender] = PendingMessage(sender, message, timestamp, messageType, imagePath, forwardRunnable)
        handler.postDelayed(forwardRunnable, DEBOUNCE_DELAY_MS)
        AppLogger.d(TAG, "Scheduled forwarding for $sender in ${DEBOUNCE_DELAY_MS}ms")
    }
    
    private fun extractAndSaveImage(extras: Bundle, timestamp: Long): String? {
        try {
            AppLogger.d(TAG, "=== Attempting image extraction ===")
            
            var bitmap: Bitmap? = null
            
            // Try EXTRA_PICTURE first (BigPictureStyle notifications)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bitmap = extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                if (bitmap != null) AppLogger.d(TAG, "Found image via EXTRA_PICTURE")
            } else {
                @Suppress("DEPRECATION")
                bitmap = extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
                if (bitmap != null) AppLogger.d(TAG, "Found image via EXTRA_PICTURE (legacy)")
            }
            
            // Try EXTRA_LARGE_ICON_BIG if no picture found
            if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bitmap = extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Bitmap::class.java)
                if (bitmap != null) AppLogger.d(TAG, "Found image via EXTRA_LARGE_ICON_BIG")
            }
            
            // Try EXTRA_LARGE_ICON as fallback (but skip small contact avatars)
            if (bitmap == null) {
                val iconBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap
                }
                // Only use if it's reasonably large (not just a contact avatar)
                if (iconBitmap != null && iconBitmap.width > 200 && iconBitmap.height > 200) {
                    bitmap = iconBitmap
                    AppLogger.d(TAG, "Found image via EXTRA_LARGE_ICON (${iconBitmap.width}x${iconBitmap.height})")
                } else if (iconBitmap != null) {
                    AppLogger.d(TAG, "Skipping small EXTRA_LARGE_ICON (${iconBitmap.width}x${iconBitmap.height})")
                }
            }
            
            // Check MessagingStyle for images (EXTRA_MESSAGES)
            if (bitmap == null) {
                // Check if images are reduced (Google Messages privacy feature)
                val reducedImages = extras.getBoolean("android.reduced.images", false)
                if (reducedImages) {
                    AppLogger.d(TAG, "android.reduced.images is TRUE - Google Messages does not include image data in notifications")
                }
                
                AppLogger.d(TAG, "Checking EXTRA_MESSAGES for images...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // EXTRA_MESSAGES can be either Parcelable[] or ArrayList<Bundle>
                    val messagesArray = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                    val messagesList = extras.getParcelableArrayList<Bundle>(Notification.EXTRA_MESSAGES)
                    
                    AppLogger.d(TAG, "EXTRA_MESSAGES as array: ${messagesArray?.size ?: 0}, as list: ${messagesList?.size ?: 0}")
                    
                    // Try Parcelable[] first
                    messagesArray?.forEachIndexed { index, parcelable ->
                        if (parcelable is Bundle) {
                            val messageBundle = parcelable
                            AppLogger.d(TAG, "Message[$index] keys: ${messageBundle.keySet()}")
                            messageBundle.keySet().forEach { key ->
                                AppLogger.d(TAG, "  $key = ${messageBundle.get(key)}")
                            }
                            
                            // Try different ways to get the image URI
                            val dataUri = messageBundle.getString("uri") 
                                ?: (messageBundle.get("uri") as? Uri)?.toString()
                            val dataMimeType = messageBundle.getString("type")
                            
                            AppLogger.d(TAG, "Message[$index] uri: $dataUri, type: $dataMimeType")
                            
                            if (bitmap == null && dataUri != null && (dataMimeType?.startsWith("image/") == true || dataUri.contains("image"))) {
                                try {
                                    AppLogger.d(TAG, "Attempting to load image from URI: $dataUri")
                                    val inputStream = contentResolver.openInputStream(Uri.parse(dataUri))
                                    inputStream?.use { stream ->
                                        bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                        if (bitmap != null) {
                                            AppLogger.d(TAG, "Successfully loaded image from MessagingStyle")
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e(TAG, "Failed to load image from URI: $dataUri - ${e.message}")
                                }
                            }
                        }
                    }
                    
                    // Fallback to ArrayList<Bundle>
                    if (bitmap == null) {
                        messagesList?.forEachIndexed { index, messageBundle ->
                            AppLogger.d(TAG, "MessageList[$index] keys: ${messageBundle.keySet()}")
                            
                            val dataUri = messageBundle.getString("uri") 
                                ?: (messageBundle.get("uri") as? Uri)?.toString()
                            val dataMimeType = messageBundle.getString("type")
                            
                            if (bitmap == null && dataUri != null && (dataMimeType?.startsWith("image/") == true || dataUri.contains("image"))) {
                                try {
                                    val inputStream = contentResolver.openInputStream(Uri.parse(dataUri))
                                    inputStream?.use { stream ->
                                        bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e(TAG, "Failed to load image from URI: $dataUri - ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
            
            // Also check EXTRA_PICTURE_CONTENT_DESCRIPTION as indicator
            val pictureDesc = extras.getCharSequence(Notification.EXTRA_PICTURE_CONTENT_DESCRIPTION)
            if (pictureDesc != null) {
                AppLogger.d(TAG, "Picture content description: $pictureDesc")
            }
            
            if (bitmap == null) {
                AppLogger.d(TAG, "No image found in notification")
                return null
            }
            
            AppLogger.d(TAG, "Saving image (${bitmap!!.width}x${bitmap!!.height})...")
            
            // Save bitmap to internal storage
            val imageDir = File(filesDir, "message_images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }
            
            // Clean up old images (older than 1 hour)
            imageDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 3600000) {
                    file.delete()
                }
            }
            
            val imageFile = File(imageDir, "img_$timestamp.jpg")
            FileOutputStream(imageFile).use { out ->
                bitmap!!.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            AppLogger.d(TAG, "Image saved to: ${imageFile.absolutePath}")
            return imageFile.absolutePath
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error extracting image from notification: ${e.message}")
            return null
        }
    }
    
    private fun hasImageAttachment(extras: Bundle): Boolean {
        // Check if EXTRA_PICTURE exists
        if (extras.containsKey(Notification.EXTRA_PICTURE)) return true
        
        // Check MessagingStyle messages for image attachments
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("UNCHECKED_CAST")
            val messages = extras.getParcelableArrayList<Bundle>(Notification.EXTRA_MESSAGES)
            messages?.forEach { messageBundle ->
                val dataMimeType = messageBundle.getString("type")
                if (dataMimeType?.startsWith("image/") == true) return true
            }
        }
        
        return false
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
