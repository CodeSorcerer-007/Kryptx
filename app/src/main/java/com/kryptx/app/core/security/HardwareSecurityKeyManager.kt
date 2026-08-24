package com.kryptx.app.core.security

import android.content.Context
import android.nfc.Tag
import com.kryptx.app.core.crypto.SecureMemory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * High-level Hardware Security Key (FIDO2 / YubiKey NFC) Orchestrator.
 * Manages physical key pairing state, challenge generation, and master key salt injection.
 */
class HardwareSecurityKeyManager(
    private val context: Context? = null
) {
    private val secureRandom = SecureRandom()

    data class KeyPairingState(
        val isEnrolled: Boolean,
        val keyLabel: String = "",
        val pairedKeyUidHash: String = "",
        val challengeSalt: String = ""
    )

    /**
     * Generates a fresh 32-byte cryptographically secure challenge for the hardware key.
     */
    fun generateFreshChallenge(): ByteArray {
        val challenge = ByteArray(32)
        secureRandom.nextBytes(challenge)
        return challenge
    }

    /**
     * Verifies and processes an NFC tag against the enrolled hardware security key.
     * Returns the derived hardware secret bytes or null if invalid/mismatched.
     */
    fun processTagResponse(
        tag: Tag,
        expectedUidHash: String?,
        challenge: ByteArray
    ): ByteArray? {
        val rawResponse = NfcHardwareKeyManager.processNfcChallenge(tag, challenge) ?: return null

        if (expectedUidHash != null && expectedUidHash.isNotBlank()) {
            val tagHash = hashTagUid(tag.id)
            if (tagHash != expectedUidHash) {
                SecureMemory.wipe(rawResponse)
                return null
            }
        }

        return rawResponse
    }

    /**
     * Hashes an NFC Tag UID with SHA-256 for secure, non-reversible identification.
     */
    fun hashTagUid(tagId: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(tagId)
        return Base64.getEncoder().encodeToString(digest)
    }

    /**
     * Checks if NFC is supported and active on the device.
     */
    fun isHardwareAvailable(): Boolean {
        if (context == null) return false
        return NfcHardwareKeyManager.hasNfc(context) && NfcHardwareKeyManager.isNfcEnabled(context)
    }
}
