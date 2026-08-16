package com.kryptx.app.core.security

interface IClipboardSecurityManager {
    fun copySensitiveText(label: String, text: String, timeoutSeconds: Int = 30)
    fun clearIfMatching(text: String)
    fun clearNow()
}
