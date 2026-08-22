package com.kryptx.app.core.database

import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.flow.Flow
import javax.crypto.Cipher

interface VaultRepository {
    fun hasVault(): Boolean
    fun isBiometricsConfigured(): Boolean

    suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun setupBiometrics(): KryptxResult<Unit>
    suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit>
    suspend fun unlockWithBiometrics(): KryptxResult<Unit>
    suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit>
    fun getBiometricDecryptCipher(): Cipher?
    fun getBiometricEncryptCipher(): Cipher?
    suspend fun disableBiometrics()
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): KryptxResult<Unit>

    fun hasDuressPassword(): Boolean
    suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit>
    suspend fun removeDuressPassword()

    fun getItems(): Flow<List<VaultItem>>
    suspend fun getItemById(id: String): VaultItem?
    suspend fun saveItem(item: VaultItem): KryptxResult<Unit>
    suspend fun deleteItem(itemId: String): KryptxResult<Unit>
    suspend fun toggleFavorite(itemId: String): KryptxResult<Unit>
    suspend fun recordItemUsage(itemId: String): KryptxResult<Unit>

    suspend fun computeSecurityAudit(): SecurityAuditReport
    suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload>
    suspend fun exportPlaintextJson(): KryptxResult<String>
    suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): KryptxResult<Int>
    suspend fun importItems(items: List<VaultItem>): KryptxResult<Int>
    suspend fun resetVault()
}
