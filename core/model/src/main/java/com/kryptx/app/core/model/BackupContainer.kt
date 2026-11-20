package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class BackupHeader(
    val app: String = CURRENT_APP_NAME,
    val version: String = CURRENT_APP_VERSION,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val kdfAlgorithm: String = "Argon2id",
    val kdfIterations: Int = 0,
    val saltBase64: String = "",
    val ivBase64: String = "",
    val isPostQuantum: Boolean = false,
    val pqcEncapsulationBase64: String? = null,
    val checksumSha256: String? = null,
    val hmacSha256Base64: String? = null
) {
    companion object {
        const val CURRENT_APP_NAME = "Kryptx"
        const val CURRENT_APP_VERSION = "2.5.1"
        const val CURRENT_FORMAT_VERSION = 3
    }
}

@Immutable
@Serializable
data class EncryptedBackupPayload(
    val header: BackupHeader,
    val ciphertextBase64: String
)

