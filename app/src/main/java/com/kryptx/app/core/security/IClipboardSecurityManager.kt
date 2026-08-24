package com.kryptx.app.core.security

import kotlinx.coroutines.flow.StateFlow

interface IClipboardSecurityManager {
    val remainingSeconds: StateFlow<Int>
    fun copySensitiveText(label: String, text: String, timeoutSeconds: Int = 30)
    fun clearIfMatching(text: String)
    fun clearNow()
}
