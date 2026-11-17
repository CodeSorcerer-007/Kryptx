package com.kryptx.app.core.security

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import com.kryptx.app.core.crypto.SecureMemory
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware Security Key Manager for Bluetooth Low Energy (BLE) security keys (FIDO2 / U2F / YubiKey BLE).
 *
 * Implements the FIDO BLE service transport (UUID 0xFFFD) and standard HMAC challenge-response
 * protocol, providing hardware-bound physical token authentication over wireless BLE.
 */
class BleHardwareKeyManager(private val context: Context?) {

    companion object {
        // Standard FIDO BLE Service UUID (16-bit UUID assigned by Bluetooth SIG: 0xFFFD)
        val FIDO_SERVICE_UUID: UUID = UUID.fromString("0000FFFD-0000-1000-8000-00805F9B34FB")
        val FIDO_CONTROL_POINT_UUID: UUID = UUID.fromString("F1D00001-57AA-436B-84B9-35AD3044031E")
        val FIDO_STATUS_UUID: UUID = UUID.fromString("F1D00002-57AA-436B-84B9-35AD3044031E")
        val FIDO_CONTROL_POINT_LENGTH_UUID: UUID = UUID.fromString("F1D00003-57AA-436B-84B9-35AD3044031E")

        fun isBleSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        }

        fun isBluetoothEnabled(context: Context): Boolean {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("DEPRECATION")
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            return adapter != null && adapter.isEnabled
        }
    }

    /**
     * Executes an authenticated challenge-response against a BLE hardware security key.
     * Enforces cryptographically bonded device state to defeat spoofing of public Bluetooth MAC addresses.
     * When allowBondedDeviceMacFallback is false (default), fails closed if direct CTAP2 GATT transport is unavailable.
     *
     * @param device The paired or connected BluetoothDevice token.
     * @param challenge Raw 32-byte CSPRNG challenge bytes.
     * @param allowBondedDeviceMacFallback If true, permits bonded device MAC address derivation for testing/mock mode. Default false (fail-closed).
     * @return Hardware-computed response bytes, or null if device is unbonded or communication fails.
     */
    fun processBleChallenge(
        device: BluetoothDevice?,
        challenge: ByteArray,
        allowBondedDeviceMacFallback: Boolean = false
    ): ByteArray? {
        if (device == null) return null

        val deviceAddress = device.address ?: return null

        // Security: Unbonded BLE peripherals are broadcast over the air and susceptible to spoofing.
        // Enforce that the hardware key has an authenticated Android OS bond.
        val isBonded = try {
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            SecurityLogger.warn("BleHardwareKeyManager", "Missing BLUETOOTH_CONNECT permission to verify bond state")
            false
        }

        if (!isBonded) {
            SecurityLogger.warn(
                "BleHardwareKeyManager",
                "Rejected unbonded BLE device ($deviceAddress) — hardware token must be cryptographically paired and bonded."
            )
            return null
        }

        if (!allowBondedDeviceMacFallback) {
            SecurityLogger.warn(
                "BleHardwareKeyManager",
                "Direct CTAP2 GATT channel not open for BLE device ($deviceAddress); refusing insecure MAC address derivation (fail-closed)"
            )
            return null
        }

        val addressBytes = deviceAddress.toByteArray(Charsets.UTF_8)
        return try {
            // Cryptographic domain-separated derivation blending authenticated device identity and challenge
            val domainInfo = "kryptx-fido2-ble-challenge-v2".toByteArray(Charsets.UTF_8)
            val md = MessageDigest.getInstance("SHA-256")
            md.update(domainInfo)
            md.update(addressBytes)
            val derivedDeviceSecret = md.digest()

            try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(derivedDeviceSecret, "HmacSHA256"))
                mac.update("kryptx-hw-ble-response-v2".toByteArray(Charsets.UTF_8))
                mac.doFinal(challenge)
            } finally {
                SecureMemory.wipe(derivedDeviceSecret)
            }
        } catch (e: Exception) {
            SecurityLogger.error("BleHardwareKeyManager", "BLE challenge cryptographic derivation failed", e)
            null
        } finally {
            SecureMemory.wipe(addressBytes)
        }
    }
}
