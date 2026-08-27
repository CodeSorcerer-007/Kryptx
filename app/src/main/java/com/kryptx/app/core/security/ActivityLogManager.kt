package com.kryptx.app.core.security

import android.content.Context
import com.kryptx.app.core.database.KryptxDatabaseHelper
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Serializable
data class ActivityEvent(
    val timestamp: Long,
    val type: String,
    val description: String
)

class ActivityLogManager(context: Context) {
    private val dbHelper = (context.applicationContext as com.kryptx.app.KryptxApplication).dbHelper

    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun loadEvents() {
        scope.launch {
            try {
                _events.value = dbHelper.getActivityEvents(1000)
            } catch (e: Exception) {
            }
        }
    }

    fun logEvent(type: String, description: String) {
        val newEvent = ActivityEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            description = description
        )
        
        val currentList = _events.value.toMutableList()
        currentList.add(0, newEvent)
        if (currentList.size > 1000) {
            currentList.removeAt(currentList.size - 1)
        }
        _events.value = currentList

        scope.launch {
            try {
                dbHelper.insertActivityEvent(newEvent.timestamp, newEvent.type, newEvent.description)
                dbHelper.enforceActivityEventLimit(1000)
            } catch (e: Exception) {
            }
        }
    }

    fun clearLog() {
        _events.value = emptyList()
        scope.launch {
            try {
                dbHelper.clearActivityEvents()
            } catch (e: Exception) {
            }
        }
    }
}
