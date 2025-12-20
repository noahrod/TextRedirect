package com.thenoahnoah.textredirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Not an SMS received action, ignoring")
            return
        }

        // Check if service is enabled
        val prefs = context.getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean("service_enabled", false)
        
        Log.d(TAG, "Service enabled status: $isServiceEnabled")
        
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled, ignoring SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        Log.d(TAG, "Received ${messages.size} SMS messages")
        
        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress
            val messageBody = smsMessage.messageBody
            val timestamp = smsMessage.timestampMillis
            
            Log.d(TAG, "SMS received from: $sender, message: $messageBody")
            
            // Start the service to forward the message
            val serviceIntent = Intent(context, MessageForwardingService::class.java).apply {
                putExtra("sender", sender)
                putExtra("message", messageBody)
                putExtra("timestamp", timestamp)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                    Log.d(TAG, "Started foreground service")
                } else {
                    context.startService(serviceIntent)
                    Log.d(TAG, "Started service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }
    }
}
