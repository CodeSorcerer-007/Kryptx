package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupHeader(
    val app: String = "Kryptx",
    val version: String = "1.0.0",
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int = 600_000,
    val saltBase64: String = "",
    val ivBase64: String = ""
)

@Serializable
data class EncryptedBackupPayload(
    val header: BackupHeader,
    val ciphertextBase64: String
)

@Serializable
data class PlaintextBackupData(
    val header: BackupHeader,
    val items: List<VaultItem>
)
