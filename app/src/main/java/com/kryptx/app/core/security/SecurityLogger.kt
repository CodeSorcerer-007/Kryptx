package com.kryptx.app.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Ephemeral In-Memory Security Diagnostic Logger for Kryptx.
 * Stores runtime diagnostic traces and OEM compatibility warnings in a fixed-size
 * ring buffer residing strictly in volatile RAM. Never persisted to disk or flash storage.
 * Automatically cleared when vault locks to uphold zero-forensics guarantees.
 */
object SecurityLogger {

    enum class Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwableDetails: String? = null
    )

    private const val MAX_CAPACITY = 250
    private val counter = AtomicLong(0)
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(MAX_CAPACITY)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun trace(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.TRACE, tag, message, throwable)
    }

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(
            id = counter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwableDetails = throwable?.let { "${it.javaClass.simpleName}: ${it.message ?: "No message"}" }
        )

        synchronized(lock) {
            if (buffer.size >= MAX_CAPACITY) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            _logs.value = buffer.toList().asReversed()
        }
    }

    fun getEntries(): List<LogEntry> {
        synchronized(lock) {
            return buffer.toList().asReversed()
        }
    }

    /**
     * Purges all diagnostic traces from volatile memory.
     * Invoked on vault lock or duress wipe.
     */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _logs.value = emptyList()
        }
    }
}
