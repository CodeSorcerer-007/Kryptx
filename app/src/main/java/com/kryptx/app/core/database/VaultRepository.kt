package com.kryptx.app.core.database

import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun hasVault(): Boolean
    fun isBiometricsConfigured(): Boolean

    suspend fun setupNewVault(masterPassword: CharArray): Boolean
    suspend fun unlockWithPassword(masterPassword: CharArray): Boolean
    suspend fun setupBiometrics(): Boolean
    suspend fun unlockWithBiometrics(): Boolean
    suspend fun unlockWithBiometricCipher(cipher: javax.crypto.Cipher): Boolean
    fun getBiometricDecryptCipher(): javax.crypto.Cipher?
    suspend fun disableBiometrics()
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): Boolean

    fun hasDuressPassword(): Boolean
    suspend fun setupDuressPassword(duressPassword: CharArray): Boolean
    suspend fun removeDuressPassword()

    fun getItems(): Flow<List<VaultItem>>
    suspend fun getItemById(id: String): VaultItem?
    suspend fun saveItem(item: VaultItem): Boolean
    suspend fun deleteItem(itemId: String): Boolean
    suspend fun toggleFavorite(itemId: String): Boolean
    suspend fun recordItemUsage(itemId: String): Boolean

    suspend fun computeSecurityAudit(): SecurityAuditReport
    suspend fun exportEncryptedBackup(exportPassword: CharArray): EncryptedBackupPayload?
    suspend fun exportPlaintextJson(): String?
    suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): Int
    suspend fun importItems(items: List<VaultItem>): Int
    suspend fun resetVault()
}
