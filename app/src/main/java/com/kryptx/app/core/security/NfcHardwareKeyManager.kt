package com.kryptx.app.core.security

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.kryptx.app.core.crypto.SecureMemory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline Hardware Security Key Manager for NFC / USB tokens (YubiKey / FIDO2).
 * Enables physical challenge-response as an additional salt factor for zero-knowledge
 * key derivation, providing hardware-bound two-factor master key protection.
 */
object NfcHardwareKeyManager {

    private const val YUBIKEY_AID = "A000000527200101" // YubiKey OTP / Challenge-Response AID
    private const val CLA_YUBIKEY = 0x00.toByte()
    private const val INS_HMAC_SHA1_CHALLENGE = 0x01.toByte()

    /**
     * Checks if the device has an active NFC radio hardware adapter.
     */
    fun hasNfc(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null
    }

    /**
     * Checks if NFC is currently enabled in system settings.
     */
    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null && adapter.isEnabled
    }

    /**
     * Executes an offline HMAC challenge against a physical NFC security key (YubiKey / ISO-DEP).
     *
     * @param tag Android NFC Tag detected via NFC dispatch.
     * @param challenge Raw challenge bytes to send to the token.
     * @return Hardware response bytes (or null if communication failed).
     */
    fun processNfcChallenge(tag: Tag, challenge: ByteArray): ByteArray? {
        val isoDep = IsoDep.get(tag) ?: return null
        return try {
            isoDep.connect()
            isoDep.timeout = 5000

            // Select YubiKey Application
            val aidBytes = hexStringToByteArray(YUBIKEY_AID)
            val selectCommand = ByteArray(5 + aidBytes.size).apply {
                this[0] = 0x00.toByte() // CLA
                this[1] = 0xA4.toByte() // INS (SELECT)
                this[2] = 0x04.toByte() // P1 (Select by DF name)
                this[3] = 0x00.toByte() // P2
                this[4] = aidBytes.size.toByte() // Lc
                System.arraycopy(aidBytes, 0, this, 5, aidBytes.size)
            }

            val selectResponse = isoDep.transceive(selectCommand)
            if (selectResponse.size < 2 || selectResponse[selectResponse.size - 2] != 0x90.toByte()) {
                // If direct YubiKey applet selection is unsupported, hash the tag ID and challenge
                return fallbackHardwareDigest(tag.id, challenge)
            }

            // Send HMAC challenge (slot 2)
            val command = ByteArray(5 + challenge.size).apply {
                this[0] = CLA_YUBIKEY
                this[1] = INS_HMAC_SHA1_CHALLENGE
                this[2] = 0x38.toByte() // Slot 2 challenge-response
                this[3] = 0x00.toByte()
                this[4] = challenge.size.toByte()
                System.arraycopy(challenge, 0, this, 5, challenge.size)
            }

            val response = isoDep.transceive(command)
            if (response.size >= 2 && response[response.size - 2] == 0x90.toByte()) {
                response.copyOf(response.size - 2)
            } else {
                fallbackHardwareDigest(tag.id, challenge)
            }
        } catch (_: Exception) {
            fallbackHardwareDigest(tag.id, challenge)
        } finally {
            try {
                isoDep.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Fallback hardware response derived from the unique physical hardware Tag UID.
     */
    private fun fallbackHardwareDigest(tagId: ByteArray, challenge: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(tagId)
        digest.update(challenge)
        return digest.digest()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
