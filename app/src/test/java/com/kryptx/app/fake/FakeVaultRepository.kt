package com.kryptx.app.fake

import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher

class FakeVaultRepository : VaultRepository {

    private var hasVaultSetup = false
    private var biometricsConfigured = false
    private val _itemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    private var storedPassword = "MasterPassword123!"

    override fun hasVault(): Boolean = hasVaultSetup

    override fun isBiometricsConfigured(): Boolean = biometricsConfigured

    override fun getBiometricDecryptCipher(): Cipher? = null
    override fun getBiometricEncryptCipher(): Cipher? = null

    override suspend fun setupNewVault(masterPassword: CharArray): Boolean {
        hasVaultSetup = true
        storedPassword = String(masterPassword)
        return true
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): Boolean {
        return if (String(masterPassword) == storedPassword) {
            true
        } else {
            false
        }
    }

    override suspend fun setupBiometrics(): Boolean {
        biometricsConfigured = true
        return true
    }

    override suspend fun setupBiometricsWithCipher(cipher: Cipher): Boolean {
        biometricsConfigured = true
        return true
    }

    override suspend fun unlockWithBiometrics(): Boolean = biometricsConfigured

    override suspend fun unlockWithBiometricCipher(cipher: Cipher): Boolean = biometricsConfigured

    override suspend fun disableBiometrics() {
        biometricsConfigured = false
    }

    override suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): Boolean {
        if (String(currentPassword) != storedPassword) return false
        storedPassword = String(newPassword)
        return true
    }

    private var duressPassword: String? = null
    override fun hasDuressPassword(): Boolean = duressPassword != null
    override suspend fun setupDuressPassword(duressPassword: CharArray): Boolean {
        this.duressPassword = String(duressPassword)
        return true
    }
    override suspend fun removeDuressPassword() {
        this.duressPassword = null
    }

    override fun getItems(): Flow<List<VaultItem>> = _itemsFlow.asStateFlow()

    override suspend fun getItemById(id: String): VaultItem? {
        return _itemsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun saveItem(item: VaultItem): Boolean {
        val current = _itemsFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            current[idx] = item
        } else {
            current.add(0, item)
        }
        _itemsFlow.value = current
        return true
    }

    override suspend fun deleteItem(itemId: String): Boolean {
        val current = _itemsFlow.value.filter { it.id != itemId }
        _itemsFlow.value = current
        return true
    }

    override suspend fun toggleFavorite(itemId: String): Boolean {
        val current = _itemsFlow.value.firstOrNull { it.id == itemId } ?: return false
        saveItem(current.copy(isFavorite = !current.isFavorite))
        return true
    }

    override suspend fun recordItemUsage(itemId: String): Boolean {
        val current = _itemsFlow.value.firstOrNull { it.id == itemId } ?: return false
        saveItem(current.copy(lastUsedAt = System.currentTimeMillis()))
        return true
    }

    override suspend fun computeSecurityAudit(): SecurityAuditReport {
        val items = _itemsFlow.value
        return SecurityAuditReport(
            overallScore = 95,
            healthGrade = "A+",
            compromisedCount = 0,
            weakCount = 0,
            reusedCount = 0,
            oldPasswordCount = 0,
            missing2faCount = 0,
            issues = emptyList()
        )
    }

    override suspend fun exportEncryptedBackup(exportPassword: CharArray): EncryptedBackupPayload {
        return EncryptedBackupPayload(
            header = BackupHeader(),
            ciphertextBase64 = "encrypted_test_payload"
        )
    }

    override suspend fun exportPlaintextJson(): String {
        return "[]"
    }

    override suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): Int {
        return 1
    }

    override suspend fun importItems(items: List<VaultItem>): Int {
        val current = _itemsFlow.value.toMutableList()
        current.addAll(items)
        _itemsFlow.value = current
        return items.size
    }

    override suspend fun resetVault() {
        hasVaultSetup = false
        biometricsConfigured = false
        _itemsFlow.value = emptyList()
    }
}
