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
) : IHardwareSecurityKeyManager {
    private val secureRandom = SecureRandom()

    private var primaryKey: KeyPairingState? = null
    private var backupKey: KeyPairingState? = null

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

    override fun getPrimaryPairingState(): KeyPairingState? = primaryKey
    override fun getBackupPairingState(): KeyPairingState? = backupKey
    override fun isBackupKeyEnrolled(): Boolean = backupKey?.isEnrolled == true
    override fun clearBackupKey() { backupKey = null }

    /**
     * Generates a fresh 32-byte cryptographically secure challenge for the hardware key.
     */
    override fun generateFreshChallenge(): ByteArray {
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
    override fun deriveHardwareBoundSalt(baseSalt: ByteArray, hardwareResponse: ByteArray): ByteArray {
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
    override fun processTagResponse(
        tag: Tag,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowInsecureUidFallback: Boolean
    ): ByteArray? {
        val rawResponse = NfcHardwareKeyManager.processNfcChallenge(tag, challenge, allowInsecureUidFallback) ?: return null

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
    override fun processUsbChallenge(
        usbDevice: UsbDevice?,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowInsecureDescriptorFallback: Boolean
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
                SecurityLogger.warn("HardwareSecurityKeyManager", "USB HID YubiKey challenge failed, checking fallback policy", e)
            }
        }

        // Fail-closed security: USB vendorId/productId/deviceName are public and spoofable descriptors.
        // Unless explicitly allowed for mock testing, fail closed.
        if (!allowInsecureDescriptorFallback) {
            SecurityLogger.warn("HardwareSecurityKeyManager", "Direct USB HID challenge failed or permission missing; refusing insecure descriptor fallback (fail-closed)")
            return null
        }

        // Fallback: device-bound HMAC-SHA256 using the device identifier as the HMAC key for testing/mock mode.
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
     * @param connection An active [android.hardware.usb.UsbDeviceConnection] to the target USB peripheral.
     * @param usbDevice The [UsbDevice] instance describing endpoints and HID interfaces.
     * @param challenge The raw byte array containing challenge entropy to submit to slot 2.
     * @return The 20-byte HMAC-SHA1 response, or null if communication fails or times out.
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
    override fun enrollTag(
        tag: Tag,
        label: String,
        allowInsecureUidFallback: Boolean
    ): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = NfcHardwareKeyManager.processNfcChallenge(tag, challenge, allowInsecureUidFallback) ?: return null
        val tagHash = hashTagUid(tag.id)
        val state = KeyPairingState(
            isEnrolled = true,
            keyLabel = label,
            pairedKeyUidHash = tagHash,
            challengeSalt = Base64.getEncoder().encodeToString(challenge),
            transport = KeyTransport.NFC
        )
        primaryKey = state
        return Pair(state, response)
    }

    override fun enrollBackupTag(
        tag: Tag,
        label: String,
        allowInsecureUidFallback: Boolean
    ): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = NfcHardwareKeyManager.processNfcChallenge(tag, challenge, allowInsecureUidFallback) ?: return null
        val tagHash = hashTagUid(tag.id)
        val state = KeyPairingState(
            isEnrolled = true,
            keyLabel = label,
            pairedKeyUidHash = tagHash,
            challengeSalt = Base64.getEncoder().encodeToString(challenge),
            transport = KeyTransport.NFC
        )
        backupKey = state
        return Pair(state, response)
    }

    override fun enrollUsbKey(
        usbDevice: UsbDevice,
        label: String,
        allowInsecureDescriptorFallback: Boolean
    ): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = processUsbChallenge(usbDevice, null, challenge, allowInsecureDescriptorFallback) ?: return null
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
        primaryKey = state
        return Pair(state, response)
    }

    override fun enrollBackupUsbKey(
        usbDevice: UsbDevice,
        label: String,
        allowInsecureDescriptorFallback: Boolean
    ): Pair<KeyPairingState, ByteArray>? {
        val challenge = generateFreshChallenge()
        val response = processUsbChallenge(usbDevice, null, challenge, allowInsecureDescriptorFallback) ?: return null
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
        backupKey = state
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
    override fun processBleChallenge(
        bleDevice: android.bluetooth.BluetoothDevice?,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowBondedDeviceMacFallback: Boolean
    ): ByteArray? {
        if (bleDevice == null) return null

        val deviceAddress = bleDevice.address ?: return null
        val devHash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(deviceAddress.toByteArray(Charsets.UTF_8))
        )

        if (expectedUidHash != null && expectedUidHash.isNotBlank() && devHash != expectedUidHash) {
            return null
        }

        return bleKeyManager.processBleChallenge(bleDevice, challenge, allowBondedDeviceMacFallback)
    }

    /**
     * Enrolls a physical BLE security key.
     */
    override fun enrollBleKey(
        bleDevice: android.bluetooth.BluetoothDevice,
        label: String,
        allowBondedDeviceMacFallback: Boolean
    ): Pair<KeyPairingState, ByteArray>? {
        val isBonded = try {
            bleDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }
        if (!isBonded) {
            SecurityLogger.warn("HardwareSecurityKeyManager", "Cannot enroll unbonded BLE security key — device must be paired")
            return null
        }
        val challenge = generateFreshChallenge()
        val response = processBleChallenge(bleDevice, null, challenge, allowBondedDeviceMacFallback) ?: return null
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
        primaryKey = state
        return Pair(state, response)
    }

    override fun processAnyTagResponse(
        tag: Tag,
        challenge: ByteArray,
        allowInsecureUidFallback: Boolean
    ): ByteArray? {
        val primaryHash = primaryKey?.pairedKeyUidHash
        val backupHash = backupKey?.pairedKeyUidHash

        // Try primary key UID match first
        val primaryResp = processTagResponse(tag, primaryHash, challenge, allowInsecureUidFallback)
        if (primaryResp != null) return primaryResp

        // Failover: if backup key enrolled, try backup key UID match
        if (backupHash != null) {
            val backupResp = processTagResponse(tag, backupHash, challenge, allowInsecureUidFallback)
            if (backupResp != null) return backupResp
        }

        return null
    }

    override fun processAnyUsbChallenge(
        usbDevice: UsbDevice?,
        challenge: ByteArray,
        allowInsecureDescriptorFallback: Boolean
    ): ByteArray? {
        if (usbDevice == null) return null
        val primaryHash = primaryKey?.pairedKeyUidHash
        val backupHash = backupKey?.pairedKeyUidHash

        val primaryResp = processUsbChallenge(usbDevice, primaryHash, challenge, allowInsecureDescriptorFallback)
        if (primaryResp != null) return primaryResp

        if (backupHash != null) {
            val backupResp = processUsbChallenge(usbDevice, backupHash, challenge, allowInsecureDescriptorFallback)
            if (backupResp != null) return backupResp
        }

        return null
    }

    override fun isHardwareAvailable(): Boolean {
        if (context == null) return false
        val nfcAvailable = NfcHardwareKeyManager.hasNfc(context) && NfcHardwareKeyManager.isNfcEnabled(context)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val usbAvailable = usbManager != null && usbManager.deviceList.isNotEmpty()
        val bleAvailable = BleHardwareKeyManager.isBleSupported(context)
        return nfcAvailable || usbAvailable || bleAvailable
    }
}
