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
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Check if service is enabled
        val prefs = context.getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean("service_enabled", false)
        
        if (!isServiceEnabled) {
            Log.d(TAG, "Service is disabled, ignoring SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (smsMessage in messages) {
            val sender = smsMessage.displayOriginatingAddress
            val messageBody = smsMessage.messageBody
            val timestamp = smsMessage.timestampMillis
            
            Log.d(TAG, "SMS received from: $sender")
            
            // Start the service to forward the message
            val serviceIntent = Intent(context, MessageForwardingService::class.java).apply {
                putExtra("sender", sender)
                putExtra("message", messageBody)
                putExtra("timestamp", timestamp)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
