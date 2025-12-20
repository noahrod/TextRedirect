package com.thenoahnoah.textredirect

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val formattedTimestamp: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    
    val formattedDateTime: String
        get() = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOGS = 500
    
    val logs = mutableStateListOf<LogEntry>()
    
    fun d(tag: String, message: String) {
        addLog(LogLevel.DEBUG, tag, message)
        Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        addLog(LogLevel.INFO, tag, message)
        Log.i(tag, message)
    }
    
    fun w(tag: String, message: String) {
        addLog(LogLevel.WARNING, tag, message)
        Log.w(tag, message)
    }
    
    fun e(tag: String, message: String) {
        addLog(LogLevel.ERROR, tag, message)
        Log.e(tag, message)
    }
    
    private fun addLog(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        
        // Add to beginning of list (most recent first)
        logs.add(0, entry)
        
        // Keep only MAX_LOGS entries
        if (logs.size > MAX_LOGS) {
            logs.removeAt(logs.size - 1)
        }
    }
    
    fun clear() {
        logs.clear()
        i(TAG, "Logs cleared")
    }
    
    fun saveLogs(context: Context): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val filename = "textredirect_logs_${dateFormat.format(Date())}.txt"
        
        try {
            val content = logs.reversed().joinToString("\n") { 
                "${it.formattedDateTime} [${it.level}] ${it.tag}: ${it.message}"
            }
            
            context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write(content.toByteArray())
            }
            
            return filename
        } catch (e: Exception) {
            e(TAG, "Error saving logs: ${e.message}")
            return ""
        }
    }
}
