package com.example.wakeonlanhomephone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private const val MAX_LOGS = 100

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        
        _logs.update { current ->
            val next = current.toMutableList()
            next.add(logEntry)
            if (next.size > MAX_LOGS) {
                next.removeAt(0)
            }
            next
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

