package com.kryptx.app.fake

import com.kryptx.app.core.security.IClipboardSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeClipboardSecurityManager : IClipboardSecurityManager {
    private val _remainingSeconds = MutableStateFlow(0)
    override val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    var lastCopiedLabel: String? = null
    var lastCopiedText: String? = null
    var lastTimeoutSeconds: Int = 30
    var isCleared: Boolean = false

    override fun copySensitiveText(label: String, text: String, timeoutSeconds: Int) {
        lastCopiedLabel = label
        lastCopiedText = text
        lastTimeoutSeconds = timeoutSeconds
        isCleared = false
    }

    override fun clearIfMatching(text: String) {
        if (lastCopiedText == text) {
            lastCopiedText = null
            isCleared = true
        }
    }

    override fun clearNow() {
        lastCopiedText = null
        isCleared = true
    }
}
