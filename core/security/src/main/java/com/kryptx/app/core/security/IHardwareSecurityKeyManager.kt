package com.kryptx.app.core.security

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice
import android.nfc.Tag

/**
 * Interface contract for Hardware Security Key (YubiKey NFC / USB-C OTG / CTAP2 BLE) Manager.
 * Enables dependency injection, mocking in unit tests, and multi-token redundancy.
 */
interface IHardwareSecurityKeyManager {

    /**
     * Generates a fresh 32-byte cryptographically secure challenge.
     */
    fun generateFreshChallenge(): ByteArray

    /**
     * Blends physical hardware token challenge-response bytes with the standard KDF salt
     * to form a hardware-bound master derivation salt using HKDF-SHA256.
     */
    fun deriveHardwareBoundSalt(baseSalt: ByteArray, hardwareResponse: ByteArray): ByteArray

    /**
     * Verifies and processes an NFC tag against the enrolled hardware security key.
     */
    fun processTagResponse(
        tag: Tag,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowInsecureUidFallback: Boolean = false
    ): ByteArray?

    /**
     * Verifies and processes a USB-C OTG connected YubiKey challenge via HMAC-SHA1 slot 2.
     * When allowInsecureDescriptorFallback is false (default), fails closed if direct USB HID transport is unavailable.
     */
    fun processUsbChallenge(
        usbDevice: UsbDevice?,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowInsecureDescriptorFallback: Boolean = false
    ): ByteArray?

    /**
     * Verifies and processes a BLE hardware security key challenge.
     * When allowBondedDeviceMacFallback is false (default), fails closed if direct CTAP2 GATT transport is unavailable.
     */
    fun processBleChallenge(
        bleDevice: BluetoothDevice?,
        expectedUidHash: String?,
        challenge: ByteArray,
        allowBondedDeviceMacFallback: Boolean = false
    ): ByteArray?

    /**
     * Dual-Token Redundancy: Processes an NFC tag response against either the primary OR backup key.
     */
    fun processAnyTagResponse(
        tag: Tag,
        challenge: ByteArray,
        allowInsecureUidFallback: Boolean = false
    ): ByteArray?

    /**
     * Dual-Token Redundancy: Processes a USB challenge against either the primary OR backup key.
     */
    fun processAnyUsbChallenge(
        usbDevice: UsbDevice?,
        challenge: ByteArray,
        allowInsecureDescriptorFallback: Boolean = false
    ): ByteArray?

    /**
     * Enrolls the primary physical security key.
     */
    fun enrollTag(
        tag: Tag,
        label: String = "Primary Security Key",
        allowInsecureUidFallback: Boolean = false
    ): Pair<HardwareSecurityKeyManager.KeyPairingState, ByteArray>?

    /**
     * Enrolls the primary USB-C OTG security key.
     */
    fun enrollUsbKey(
        usbDevice: UsbDevice,
        label: String = "Primary USB Security Key",
        allowInsecureDescriptorFallback: Boolean = false
    ): Pair<HardwareSecurityKeyManager.KeyPairingState, ByteArray>?

    /**
     * Enrolls the primary BLE security key.
     */
    fun enrollBleKey(
        bleDevice: BluetoothDevice,
        label: String = "Primary BLE Security Key",
        allowBondedDeviceMacFallback: Boolean = false
    ): Pair<HardwareSecurityKeyManager.KeyPairingState, ByteArray>?

    /**
     * Enrolls a secondary/backup NFC security key for failover disaster recovery.
     */
    fun enrollBackupTag(
        tag: Tag,
        label: String = "Backup Security Key",
        allowInsecureUidFallback: Boolean = false
    ): Pair<HardwareSecurityKeyManager.KeyPairingState, ByteArray>?

    /**
     * Enrolls a secondary/backup USB-C OTG security key.
     */
    fun enrollBackupUsbKey(
        usbDevice: UsbDevice,
        label: String = "Backup USB Security Key",
        allowInsecureDescriptorFallback: Boolean = false
    ): Pair<HardwareSecurityKeyManager.KeyPairingState, ByteArray>?

    fun isBackupKeyEnrolled(): Boolean

    fun getPrimaryPairingState(): HardwareSecurityKeyManager.KeyPairingState?

    fun getBackupPairingState(): HardwareSecurityKeyManager.KeyPairingState?

    fun clearBackupKey()

    /**
     * Checks if NFC, USB, or BLE Security hardware is supported and active on the device.
     */
    fun isHardwareAvailable(): Boolean
}
