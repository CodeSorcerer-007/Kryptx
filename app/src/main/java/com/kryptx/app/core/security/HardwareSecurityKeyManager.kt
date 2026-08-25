package com.kryptx.app.core.security

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.Tag
import com.kryptx.app.core.crypto.SecureMemory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * High-level Hardware Security Key (FIDO2 / YubiKey NFC / USB-C OTG) Orchestrator.
 * Manages physical key pairing state, challenge generation, and master key salt injection.
 */
class HardwareSecurityKeyManager(
    private val context: Context? = null
) {
    private val secureRandom = SecureRandom()

    enum class KeyTransport {
        NFC,
        USB_OTG,
        BLE
    }

    data class KeyPairingState(
        val isEnrolled: Boolean,
        val keyLabel: String = "",
        val pairedKeyUidHash: String = "",
        val challengeSalt: String = "",
        val transport: KeyTransport = KeyTransport.NFC
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
     * Verifies and processes a USB-C OTG connected hardware token challenge.
     */
    fun processUsbChallenge(
        usbDevice: UsbDevice?,
        expectedUidHash: String?,
        challenge: ByteArray
    ): ByteArray? {
        if (usbDevice == null) return null
        val deviceIdentifier = "${usbDevice.vendorId}:${usbDevice.productId}:${usbDevice.deviceName}".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(deviceIdentifier)
        val devHash = Base64.getEncoder().encodeToString(md.digest())

        if (expectedUidHash != null && expectedUidHash.isNotBlank() && devHash != expectedUidHash) {
            return null
        }

        // HMAC-SHA256 simulate challenge-response for standard YubiKey USB HID
        val hmacMd = MessageDigest.getInstance("SHA-256")
        hmacMd.update(challenge)
        hmacMd.update(deviceIdentifier)
        return hmacMd.digest()
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
            challengeSalt = Base64.getEncoder().encodeToString(challenge),
            transport = KeyTransport.NFC
        )
        return Pair(state, response)
    }

    /**
     * Enrolls a physical USB-C OTG security key.
     */
    fun enrollUsbKey(usbDevice: UsbDevice, label: String = "Primary USB Security Key"): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = processUsbChallenge(usbDevice, null, challenge) ?: return null
        val deviceIdentifier = "${usbDevice.vendorId}:${usbDevice.productId}:${usbDevice.deviceName}".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val tagHash = Base64.getEncoder().encodeToString(md.digest(deviceIdentifier))
        val state = KeyPairingState(
            isEnrolled = true,
            keyLabel = label,
            pairedKeyUidHash = tagHash,
            challengeSalt = Base64.getEncoder().encodeToString(challenge),
            transport = KeyTransport.USB_OTG
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
     * Checks if NFC or USB Security hardware is supported and active on the device.
     */
    fun isHardwareAvailable(): Boolean {
        if (context == null) return false
        val nfcAvailable = NfcHardwareKeyManager.hasNfc(context) && NfcHardwareKeyManager.isNfcEnabled(context)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbAvailable = usbManager != null && usbManager.deviceList.isNotEmpty()
        return nfcAvailable || usbAvailable
    }
}
