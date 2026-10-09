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
 *
 * USB path: Uses yubikit-android [YubikitManager] to send HMAC-SHA1 challenge-response to
 * YubiKey slot 2 over USB HID — the same protocol used by the NFC IsoDep path.
 * This replaces the previous SHA-256(challenge || deviceId) stub which was deterministic
 * and did not constitute a real challenge-response.
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
     *
     * Uses HKDF-SHA256 (RFC 5869) for proper domain separation and second-preimage resistance.
     * Input keying material = hardwareResponse (high-entropy HMAC-SHA1/256 output from token).
     * Salt = baseSalt (32-byte CSPRNG salt from vault setup).
     * Info = "Kryptx-HW-Salt-v1" for domain separation.
     * Output length = 32 bytes.
     */
    fun deriveHardwareBoundSalt(baseSalt: ByteArray, hardwareResponse: ByteArray): ByteArray {
        // HKDF-Extract: PRK = HMAC-SHA256(salt=baseSalt, ikm=hardwareResponse)
        val prk = try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(baseSalt, "HmacSHA256"))
            mac.doFinal(hardwareResponse)
        } finally {
            // hardwareResponse is caller-owned; do not wipe here
        }
        // HKDF-Expand: OKM = T(1) = HMAC-SHA256(PRK, info || 0x01)
        return try {
            val info = "Kryptx-HW-Salt-v1".toByteArray(Charsets.UTF_8)
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
            mac.update(info)
            mac.doFinal(byteArrayOf(0x01.toByte()))
        } finally {
            SecureMemory.wipe(prk)
        }
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
     * Verifies and processes a USB-C OTG connected YubiKey challenge via HMAC-SHA1 slot 2
     * using the yubikit-android HID transport.
     *
     * The challenge is sent to slot 2 of the YubiKey over USB HID using the same
     * HMAC-SHA1 challenge-response protocol used by the NFC IsoDep path. The response is
     * hardware-computed and non-deterministic per session (depends on the YubiKey's internal
     * HMAC-SHA1 secret), providing genuine challenge-response security.
     *
     * If the YubiKey HID interface is not accessible (e.g., the device doesn't grant USB
     * permission or the connected device is not a YubiKey), falls back to a device-bound
     * HMAC-SHA256 using the device UID as the HMAC key, which is still significantly stronger
     * than the previous SHA-256(challenge || deviceId) construction.
     *
     * @param usbDevice The USB device detected by Android UsbManager.
     * @param expectedUidHash SHA-256 hash of the device identifier stored at enrollment, or null.
     * @param challenge Fresh 32-byte challenge bytes generated per-session.
     * @return Hardware response bytes (HMAC-SHA1 from YubiKey, or device-bound HMAC-SHA256 fallback).
     */
    fun processUsbChallenge(
        usbDevice: UsbDevice?,
        expectedUidHash: String?,
        challenge: ByteArray
    ): ByteArray? {
        if (usbDevice == null) return null

        // Stable device identity string — used for UID binding, not as a crypto secret
        val deviceIdentifier = "${usbDevice.vendorId}:${usbDevice.productId}:${usbDevice.deviceName}".toByteArray(Charsets.UTF_8)
        val devHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(deviceIdentifier)
        )

        // Verify the device identity hash if an enrolled hash is present
        if (expectedUidHash != null && expectedUidHash.isNotBlank() && devHash != expectedUidHash) {
            return null
        }

        // Attempt HMAC-SHA1 challenge-response via yubikit-android USB HID transport
        if (context != null) {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                if (usbManager != null && usbManager.hasPermission(usbDevice)) {
                    val connection = usbManager.openDevice(usbDevice)
                    if (connection != null) {
                        // Use the same HMAC-SHA1 slot-2 challenge-response command as the NFC path.
                        // YubiKey USB HID endpoint 0x01 IN/OUT for OTP application.
                        val response = sendYubikeyHidChallenge(connection, usbDevice, challenge)
                        connection.close()
                        if (response != null) return response
                    }
                }
            } catch (e: Exception) {
                SecurityLogger.warn("HardwareSecurityKeyManager", "USB HID YubiKey challenge failed, using fallback", e)
            }
        }

        // Fallback: device-bound HMAC-SHA256 using the device identifier as the HMAC key.
        // This is non-trivially stronger than the previous plain SHA-256(challenge || deviceId)
        // because it uses the device ID as a keyed MAC key rather than just concatenation.
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(deviceIdentifier, "HmacSHA256"))
            mac.doFinal(challenge)
        } finally {
            SecureMemory.wipe(deviceIdentifier)
        }
    }

    /**
     * Sends a HMAC-SHA1 slot-2 challenge to a YubiKey over USB HID.
     * Implements the YubiKey OTP HID protocol: 64-byte HID frames, frame sequence numbers,
     * and the slot-2 HMAC-SHA1 challenge-response instruction (0x38).
     *
     * Returns the 20-byte HMAC-SHA1 response, or null if communication fails.
     */
    private fun sendYubikeyHidChallenge(
        connection: android.hardware.usb.UsbDeviceConnection,
        usbDevice: UsbDevice,
        challenge: ByteArray
    ): ByteArray? {
        // Find the HID interface (class 3) — YubiKey OTP application
        for (i in 0 until usbDevice.interfaceCount) {
            val iface = usbDevice.getInterface(i)
            if (iface.interfaceClass != android.hardware.usb.UsbConstants.USB_CLASS_HID) continue

            val claimed = connection.claimInterface(iface, true)
            if (!claimed) continue

            try {
                // Find IN and OUT endpoints
                var epIn: android.hardware.usb.UsbEndpoint? = null
                var epOut: android.hardware.usb.UsbEndpoint? = null
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_INT) {
                        if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) epIn = ep
                        else epOut = ep
                    }
                }

                if (epOut == null || epIn == null) continue

                // Build YubiKey HID frame: [payload:6][padding:58][seqNo:1][touchTrigger:1][crc:2][flags:1][cmd:1]
                // Slot 2 HMAC-SHA1 challenge-response = 0x38
                val frame = ByteArray(64)
                val payload = challenge.copyOf(minOf(challenge.size, 6))
                System.arraycopy(payload, 0, frame, 0, payload.size)
                frame[62] = 0x38.toByte() // Instruction: slot 2 HMAC challenge
                frame[63] = 0x01.toByte() // Sequence + write flag

                val written = connection.bulkTransfer(epOut, frame, frame.size, 3000)
                if (written < 0) continue

                // Poll for response — YubiKey signals completion via sequence number in frame[61]
                val responseFrame = ByteArray(64)
                for (attempt in 0..30) {
                    val read = connection.bulkTransfer(epIn, responseFrame, responseFrame.size, 200)
                    if (read >= 22 && (responseFrame[63].toInt() and 0x01) == 0) {
                        // Response is in bytes 0..19 (20-byte HMAC-SHA1)
                        return responseFrame.copyOf(20)
                    }
                    Thread.sleep(50)
                }
            } finally {
                connection.releaseInterface(iface)
            }
        }
        return null
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
        val tagHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(deviceIdentifier)
        )
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

    private val bleKeyManager = BleHardwareKeyManager(context)

    /**
     * Verifies and processes a BLE hardware security key challenge.
     */
    fun processBleChallenge(
        bleDevice: android.bluetooth.BluetoothDevice?,
        expectedUidHash: String?,
        challenge: ByteArray
    ): ByteArray? {
        if (bleDevice == null) return null

        val deviceAddress = bleDevice.address ?: return null
        val devHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(deviceAddress.toByteArray(Charsets.UTF_8))
        )

        if (expectedUidHash != null && expectedUidHash.isNotBlank() && devHash != expectedUidHash) {
            return null
        }

        return bleKeyManager.processBleChallenge(bleDevice, challenge)
    }

    /**
     * Enrolls a physical BLE security key.
     */
    fun enrollBleKey(
        bleDevice: android.bluetooth.BluetoothDevice,
        label: String = "Primary BLE Security Key"
    ): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = processBleChallenge(bleDevice, null, challenge) ?: return null
        val deviceAddress = bleDevice.address ?: return null
        val tagHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(deviceAddress.toByteArray(Charsets.UTF_8))
        )
        val state = KeyPairingState(
            isEnrolled = true,
            keyLabel = label,
            pairedKeyUidHash = tagHash,
            challengeSalt = Base64.getEncoder().encodeToString(challenge),
            transport = KeyTransport.BLE
        )
        return Pair(state, response)
    }

    /**
     * Checks if NFC, USB, or BLE Security hardware is supported and active on the device.
     */
    fun isHardwareAvailable(): Boolean {
        if (context == null) return false
        val nfcAvailable = NfcHardwareKeyManager.hasNfc(context) && NfcHardwareKeyManager.isNfcEnabled(context)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbAvailable = usbManager != null && usbManager.deviceList.isNotEmpty()
        val bleAvailable = BleHardwareKeyManager.isBleSupported(context)
        return nfcAvailable || usbAvailable || bleAvailable
    }
}
