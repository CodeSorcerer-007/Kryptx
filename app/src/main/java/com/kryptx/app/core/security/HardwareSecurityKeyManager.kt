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
     * Blends physical hardware token challenge-response bytes with the standard KDF salt
     * to form a hardware-bound master derivation salt.
     */
    fun deriveHardwareBoundSalt(baseSalt: ByteArray, hardwareResponse: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(baseSalt)
        md.update(hardwareResponse)
        return md.digest()
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
     * Enrolls a physical security key by reading its tag UID and generating an initial challenge.
     */
    fun enrollTag(tag: Tag, label: String = "Primary Security Key"): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = NfcHardwareKeyManager.processNfcChallenge(tag, challenge) ?: return null
        val tagHash = hashTagUid(tag.id)
        val state = KeyPairingState(
            isEnrolled = true,
            keyLabel = label,
            pairedKeyUidHash = tagHash,
            challengeSalt = Base64.getEncoder().encodeToString(challenge)
        )
        return Pair(state, response)
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
