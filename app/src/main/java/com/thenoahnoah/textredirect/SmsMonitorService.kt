package com.thenoahnoah.textredirect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat

class SmsMonitorService : Service() {
    
    companion object {
        private const val TAG = "SmsMonitorService"
        private const val CHANNEL_ID = "SmsMonitorChannel"
        private const val NOTIFICATION_ID = 2
    }
    
    private var smsObserver: ContentObserver? = null
    private var lastSmsId = -1L
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (smsObserver == null) {
            startMonitoring()
        }
        
        return START_STICKY
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "Starting SMS monitoring")
        
        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "SMS content changed (no URI)")
                checkForNewSms()
            }
            
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "SMS content changed: $uri")
                checkForNewSms()
            }
        }
        
        // Register for multiple SMS URIs to catch all changes
        val urisToWatch = listOf(
            Telephony.Sms.CONTENT_URI,
            Telephony.Sms.Inbox.CONTENT_URI,
            Uri.parse("content://sms/"),
            Uri.parse("content://sms/inbox")
        )
        
        for (uri in urisToWatch) {
            contentResolver.registerContentObserver(uri, true, smsObserver!!)
            Log.d(TAG, "Content observer registered for $uri")
        }
        
        // Initialize lastSmsId
        checkForNewSms()
    }
    
    private fun checkForNewSms() {
        try {
            Log.d(TAG, "checkForNewSms called")
            
            val prefs = getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
            val isServiceEnabled = prefs.getBoolean("service_enabled", false)
            
            Log.d(TAG, "Service enabled: $isServiceEnabled")
            
            if (!isServiceEnabled) {
                Log.d(TAG, "Service disabled, ignoring SMS")
                return
            }
            
            Log.d(TAG, "Querying all SMS (not just inbox)...")
            
            // First, let's see ALL messages to debug - NO FILTER
            val debugCursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.TYPE, Telephony.Sms.DATE, Telephony.Sms.BODY),
                null,  // NO WHERE CLAUSE - show everything
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 10"
            )
            
            debugCursor?.use {
                Log.d(TAG, "=== ALL messages (last 10) ===")
                var index = 0
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val addr = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "null"
                    val type = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
                    val typeStr = when(type) {
                        1 -> "RECEIVED"
                        2 -> "SENT"
                        else -> "OTHER($type)"
                    }
                    Log.d(TAG, "  [$index] ID=$id, Type=$typeStr, From=$addr, Body=${body.take(30)}")
                    index++
                }
            }
            
            // Query received messages only
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.TYPE} = ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )
            
            cursor?.use {
                Log.d(TAG, "Cursor count: ${it.count}")
                
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    
                    Log.d(TAG, "Latest SMS - ID: $id, From: $sender, LastID: $lastSmsId")
                    
                    if (id != lastSmsId) {
                        if (lastSmsId == -1L) {
                            Log.d(TAG, "Initializing lastSmsId to $id")
                        } else {
                            Log.d(TAG, "New SMS detected! ID changed from $lastSmsId to $id")
                            Log.d(TAG, "From: $sender, Body: $body")
                            
                            // Forward the message
                            val serviceIntent = Intent(this, MessageForwardingService::class.java).apply {
                                putExtra("sender", sender)
                                putExtra("message", body)
                                putExtra("timestamp", timestamp)
                            }
                            
                            Log.d(TAG, "Starting MessageForwardingService...")
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        }
                        
                        lastSmsId = id
                    } else {
                        Log.d(TAG, "SMS ID unchanged, no new message")
                    }
                } else {
                    Log.d(TAG, "No SMS found in inbox")
                }
            } ?: Log.e(TAG, "Cursor is null!")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for new SMS", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        Log.d(TAG, "Service destroyed")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors SMS for forwarding"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Text Redirect")
            .setContentText("Monitoring SMS messages")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
