package com.kryptx.app.fake

import com.kryptx.app.core.security.IClipboardSecurityManager

class FakeClipboardSecurityManager : IClipboardSecurityManager {
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
