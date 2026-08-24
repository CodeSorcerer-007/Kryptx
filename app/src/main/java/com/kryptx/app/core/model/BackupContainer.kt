package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class BackupHeader(
    val app: String = "Kryptx",
    val version: String = "1.1.0",
    val formatVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val kdfAlgorithm: String = "PBKDF2WithHmacSHA256",
    val kdfIterations: Int = 600_000,
    val saltBase64: String = "",
    val ivBase64: String = "",
    val isPostQuantum: Boolean = false,
    val pqcEncapsulationBase64: String? = null,
    val checksumSha256: String? = null
)

@Immutable
@Serializable
data class EncryptedBackupPayload(
    val header: BackupHeader,
    val ciphertextBase64: String
)

@Immutable
@Serializable
data class PlaintextBackupData(
    val header: BackupHeader,
    val items: List<VaultItem>
)

