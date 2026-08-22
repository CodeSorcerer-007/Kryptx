package com.kryptx.app.fake

import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
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

    override suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit> {
        hasVaultSetup = true
        storedPassword = String(masterPassword)
        return KryptxResult.Success(Unit)
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit> {
        return if (String(masterPassword) == storedPassword) {
            KryptxResult.Success(Unit)
        } else {
            KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect password")
        }
    }

    override suspend fun setupBiometrics(): KryptxResult<Unit> {
        biometricsConfigured = true
        return KryptxResult.Success(Unit)
    }

    override suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit> {
        biometricsConfigured = true
        return KryptxResult.Success(Unit)
    }

    override suspend fun unlockWithBiometrics(): KryptxResult<Unit> =
        if (biometricsConfigured) KryptxResult.Success(Unit)
        else KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "Not configured")

    override suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit> =
        if (biometricsConfigured) KryptxResult.Success(Unit)
        else KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "Not configured")

    override suspend fun disableBiometrics() {
        biometricsConfigured = false
    }

    override suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): KryptxResult<Unit> {
        if (String(currentPassword) != storedPassword) {
            return KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect current password")
        }
        storedPassword = String(newPassword)
        return KryptxResult.Success(Unit)
    }

    private var duressPassword: String? = null
    override fun hasDuressPassword(): Boolean = duressPassword != null

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> {
        this.duressPassword = String(duressPassword)
        return KryptxResult.Success(Unit)
    }

    override suspend fun removeDuressPassword() {
        this.duressPassword = null
    }

    override fun getItems(): Flow<List<VaultItem>> = _itemsFlow.asStateFlow()

    override suspend fun getItemById(id: String): VaultItem? {
        return _itemsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun saveItem(item: VaultItem): KryptxResult<Unit> {
        val current = _itemsFlow.value.toMutableList()
        val idx = current.indexOfFirst { it.id == item.id }
        if (idx >= 0) current[idx] = item else current.add(0, item)
        _itemsFlow.value = current
        return KryptxResult.Success(Unit)
    }

    override suspend fun deleteItem(itemId: String): KryptxResult<Unit> {
        _itemsFlow.value = _itemsFlow.value.filter { it.id != itemId }
        return KryptxResult.Success(Unit)
    }

    override suspend fun toggleFavorite(itemId: String): KryptxResult<Unit> {
        val current = _itemsFlow.value.firstOrNull { it.id == itemId }
            ?: return KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
        saveItem(current.copy(isFavorite = !current.isFavorite))
        return KryptxResult.Success(Unit)
    }

    override suspend fun recordItemUsage(itemId: String): KryptxResult<Unit> {
        val current = _itemsFlow.value.firstOrNull { it.id == itemId }
            ?: return KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
        saveItem(current.copy(lastUsedAt = System.currentTimeMillis()))
        return KryptxResult.Success(Unit)
    }

    override suspend fun computeSecurityAudit(): SecurityAuditReport {
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

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    override suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload> {
        return try {
            val items = _itemsFlow.value
            val salt = com.kryptx.app.core.crypto.KeyDerivation.generateSalt()
            val derivedKey = com.kryptx.app.core.crypto.KeyDerivation.deriveKey(exportPassword, salt, iterations = 1000)
            val plaintextJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()), items)
            val ciphertext = com.kryptx.app.core.crypto.CryptoEngine.encrypt(plaintextJson.toByteArray(Charsets.UTF_8), derivedKey)
            val saltBase64 = java.util.Base64.getEncoder().encodeToString(salt)
            val ciphertextBase64 = java.util.Base64.getEncoder().encodeToString(ciphertext)
            com.kryptx.app.core.crypto.SecureMemory.wipe(derivedKey)
            val header = BackupHeader(
                app = "Kryptx", version = "1.1.0", formatVersion = 1,
                exportedAt = System.currentTimeMillis(), isEncrypted = true,
                kdfAlgorithm = "PBKDF2WithHmacSHA256", kdfIterations = 1000,
                saltBase64 = saltBase64, ivBase64 = ""
            )
            KryptxResult.Success(EncryptedBackupPayload(header, ciphertextBase64))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Export failed", e)
        }
    }

    override suspend fun exportPlaintextJson(): KryptxResult<String> {
        return KryptxResult.Success(
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()), _itemsFlow.value)
        )
    }

    override suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): KryptxResult<Int> {
        return try {
            val salt = java.util.Base64.getDecoder().decode(payload.header.saltBase64)
            val derivedKey = com.kryptx.app.core.crypto.KeyDerivation.deriveKey(importPassword, salt, payload.header.kdfIterations)
            val ciphertext = java.util.Base64.getDecoder().decode(payload.ciphertextBase64)
            val decryptedBytes = com.kryptx.app.core.crypto.CryptoEngine.decrypt(ciphertext, derivedKey)
            val plaintextJson = String(decryptedBytes, Charsets.UTF_8)
            com.kryptx.app.core.crypto.SecureMemory.wipe(derivedKey)
            com.kryptx.app.core.crypto.SecureMemory.wipe(decryptedBytes)
            val items = json.decodeFromString<List<VaultItem>>(plaintextJson)
            importItems(items)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "Import failed", e)
        }
    }

    override suspend fun importItems(items: List<VaultItem>): KryptxResult<Int> {
        val current = _itemsFlow.value.toMutableList()
        current.addAll(items)
        _itemsFlow.value = current
        return KryptxResult.Success(items.size)
    }

    override suspend fun resetVault() {
        hasVaultSetup = false
        biometricsConfigured = false
        _itemsFlow.value = emptyList()
    }
}
