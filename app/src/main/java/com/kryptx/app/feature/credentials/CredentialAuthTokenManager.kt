package com.kryptx.app.feature.credentials

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral, single-use token registry for authorizing launches of [CredentialAuthActivity].
 *
 * Prevents rogue third-party apps from launching [CredentialAuthActivity] directly with crafted Intents,
 * defending against UI confusion (phishing) and vault-state probing attacks.
 */
object CredentialAuthTokenManager {

    private val secureRandom = SecureRandom()
    private val activeTokens = ConcurrentHashMap<String, Long>()
    private const val TOKEN_TTL_MS = 5 * 60 * 1000L // 5 minutes validity

    /**
     * Generates and registers a new cryptographically random 128-bit authentication token.
     */
    fun generateToken(): String {
        cleanExpiredTokens()
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        activeTokens[token] = System.currentTimeMillis() + TOKEN_TTL_MS
        return token
    }

    /**
     * Validates and single-use consumes the provided token.
     * Returns true if the token is valid, false otherwise.
     */
    fun validateAndConsumeToken(token: String?): Boolean {
        cleanExpiredTokens()
        if (token.isNullOrBlank()) return false
        val expiry = activeTokens.remove(token) ?: return false
        return System.currentTimeMillis() <= expiry
    }

    private fun cleanExpiredTokens() {
        val now = System.currentTimeMillis()
        activeTokens.entries.removeIf { it.value < now }
    }
}
