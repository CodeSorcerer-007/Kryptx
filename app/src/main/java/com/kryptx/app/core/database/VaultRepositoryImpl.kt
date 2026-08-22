package com.kryptx.app.core.database

import android.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.IssueSeverity
import com.kryptx.app.core.model.IssueType
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.SecurityIssue
import com.kryptx.app.core.model.SecurityScoreHistoryPoint
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VaultRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val sessionManager: VaultSessionManager,
    private val keystoreManager: KeystoreManager,
    private val preferencesRepository: IPreferencesRepository? = null
) : VaultRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedAuditReport: SecurityAuditReport? = null
    @Volatile
    private var isAuditDirty: Boolean = true

    override fun hasVault(): Boolean = dbHelper.hasVaultSetup()

    override fun isBiometricsConfigured(): Boolean {
        return keystoreManager.hasBiometricKey() &&
                !dbHelper.getMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_WRAPPED_VEK).isNullOrBlank() &&
                !dbHelper.getMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_IV).isNullOrBlank()
    }

    override fun getBiometricDecryptCipher(): javax.crypto.Cipher? {
        val ivBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_IV) ?: return null
        return try {
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            keystoreManager.getDecryptCipher(iv)
        } catch (_: Exception) {
            null
        }
    }

    override fun getBiometricEncryptCipher(): javax.crypto.Cipher? = keystoreManager.getEncryptCipher()

    override suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        try {
            val salt = KeyDerivation.generateSalt()
            val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, salt)
            val vek = CryptoEngine.generateVaultKey()

            val encryptedVekPayload = CryptoEngine.encrypt(vek, derivedMasterKey)
            val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_SETUP, "true")

            dbHelper.recordSecurityScore(100)
            sessionManager.unlock(vek)

            SecureMemory.wipe(vek)
            SecureMemory.wipe(derivedMasterKey)
            SecureMemory.wipe(salt)

            isAuditDirty = true
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to create vault", e)
        }
    }

    override fun hasDuressPassword(): Boolean = dbHelper.hasDuressSetup()

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        try {
            val salt = KeyDerivation.generateSalt()
            val derivedDuressKey = KeyDerivation.deriveKey(duressPassword, salt)
            val decoyVek = CryptoEngine.generateVaultKey()
            val encryptedDecoyPayload = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

            val tokenBase64 = Base64.encodeToString(encryptedDecoyPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_DURESS, "true")

            dbHelper.provisionDefaultDecoyItems(decoyVek)

            SecureMemory.wipe(decoyVek)
            SecureMemory.wipe(derivedDuressKey)
            SecureMemory.wipe(salt)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup duress vault", e)
        }
    }

    override suspend fun removeDuressPassword() = withContext(Dispatchers.IO) {
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_SALT, "")
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_TOKEN, "")
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_DURESS, "false")
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        // 1. Try Primary Master Password
        val saltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN)

        if (saltBase64 != null && tokenBase64 != null) {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)
            val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, salt)

            try {
                val vek = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)
                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.getVaultKey()?.let { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                SecureMemory.wipe(vek)
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(salt)
                isAuditDirty = true
                return@withContext KryptxResult.Success(Unit)
            } catch (_: Exception) {
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(salt)
            }
        }

        // 2. Try Duress Decoy Password if configured
        if (hasDuressPassword()) {
            val duressSaltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_DURESS_SALT)
            val duressTokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_DURESS_TOKEN)

            if (duressSaltBase64 != null && duressTokenBase64 != null) {
                val duressSalt = Base64.decode(duressSaltBase64, Base64.NO_WRAP)
                val duressTokenBytes = Base64.decode(duressTokenBase64, Base64.NO_WRAP)
                val derivedDuressKey = KeyDerivation.deriveKey(masterPassword, duressSalt)

                try {
                    val decoyVek = CryptoEngine.decrypt(duressTokenBytes, derivedDuressKey)
                    sessionManager.unlock(decoyVek, isDecoy = true)
                    dbHelper.provisionDefaultDecoyItems(decoyVek)
                    dbHelper.loadAllDecoyItems(decoyVek)

                    SecureMemory.wipe(decoyVek)
                    SecureMemory.wipe(derivedDuressKey)
                    SecureMemory.wipe(duressSalt)
                    isAuditDirty = true
                    return@withContext KryptxResult.Success(Unit)
                } catch (_: Exception) {
                    SecureMemory.wipe(derivedDuressKey)
                    SecureMemory.wipe(duressSalt)
                }
            }
        }

        // 3. Record failed attempt throttling
        sessionManager.recordFailedAttempt()
        KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect master password")
    }

    override suspend fun setupBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val cipher = keystoreManager.getEncryptCipher()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "Could not initialize biometric cipher")
        setupBiometricsWithCipher(cipher)
    }

    override suspend fun setupBiometricsWithCipher(cipher: javax.crypto.Cipher): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val (wrappedVek, iv) = keystoreManager.wrapWithCipher(cipher, activeVek)
            val wrappedBase64 = Base64.encodeToString(wrappedVek, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_WRAPPED_VEK, wrappedBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_IV, ivBase64)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Failed to enroll biometric key", e)
        }
    }

    override suspend fun unlockWithBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val cipher = getBiometricDecryptCipher()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.KEYSTORE_INVALIDATED, "Biometric key invalidated — please re-enroll")
        unlockWithBiometricCipher(cipher)
    }

    override suspend fun unlockWithBiometricCipher(cipher: javax.crypto.Cipher): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val wrappedBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_WRAPPED_VEK)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "No biometric key stored")
        return@withContext try {
            val wrappedBytes = Base64.decode(wrappedBase64, Base64.NO_WRAP)
            val vek = keystoreManager.unwrapWithCipher(cipher, wrappedBytes)

            sessionManager.unlock(vek)
            sessionManager.getVaultKey()?.let { activeKey ->
                dbHelper.loadAllItems(activeKey)
            }
            SecureMemory.wipe(vek)
            isAuditDirty = true
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            sessionManager.recordFailedAttempt()
            KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Biometric decryption failed", e)
        }
    }

    override suspend fun disableBiometrics() = withContext(Dispatchers.IO) {
        keystoreManager.removeBiometricKey()
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_WRAPPED_VEK, "")
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_BIOMETRIC_IV, "")
    }

    override suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray
    ): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")

        val saltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_SALT)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault salt not found")
        val tokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault token not found")

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)
        val currentDerivedKey = KeyDerivation.deriveKey(currentPassword, salt)

        val verifiedVek = try {
            CryptoEngine.decrypt(tokenBytes, currentDerivedKey)
        } catch (e: Exception) {
            SecureMemory.wipe(currentDerivedKey)
            SecureMemory.wipe(salt)
            return@withContext KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect current master password")
        }
        SecureMemory.wipe(currentDerivedKey)
        SecureMemory.wipe(salt)
        SecureMemory.wipe(verifiedVek)

        // Generate new salt and re-wrap VEK
        val newSalt = KeyDerivation.generateSalt()
        val newDerivedKey = KeyDerivation.deriveKey(newPassword, newSalt)
        val newEncryptedVek = CryptoEngine.encrypt(activeVek, newDerivedKey)

        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, Base64.encodeToString(newEncryptedVek, Base64.NO_WRAP))

        if (isBiometricsConfigured()) {
            setupBiometrics()
        }

        SecureMemory.wipe(newDerivedKey)
        SecureMemory.wipe(newSalt)
        KryptxResult.Success(Unit)
    }

    override fun getItems(): Flow<List<VaultItem>> = dbHelper.itemsFlow

    override suspend fun getItemById(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext null
        dbHelper.loadItemById(id, activeVek)
    }

    override suspend fun saveItem(item: VaultItem): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        val isDecoy = sessionManager.isDecoy.value
        return@withContext try {
            val success = if (isDecoy) {
                dbHelper.saveDecoyItem(item, activeVek)
            } else {
                dbHelper.saveItem(item, activeVek)
            }
            if (success) {
                isAuditDirty = true
                KryptxResult.Success(Unit)
            } else {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to save item")
            }
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to save item", e)
        }
    }

    override suspend fun deleteItem(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val success = dbHelper.deleteItem(itemId)
            if (success) {
                isAuditDirty = true
                KryptxResult.Success(Unit)
            } else {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found or could not be deleted")
            }
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to delete item", e)
        }
    }

    override suspend fun toggleFavorite(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val success = dbHelper.toggleFavorite(itemId, activeVek)
            if (success) KryptxResult.Success(Unit)
            else KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to toggle favorite", e)
        }
    }

    override suspend fun recordItemUsage(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val success = dbHelper.recordItemUsage(itemId, activeVek)
            if (success) KryptxResult.Success(Unit)
            else KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to record usage", e)
        }
    }

    override suspend fun computeSecurityAudit(): SecurityAuditReport = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext SecurityAuditReport(
            overallScore = 100,
            healthGrade = "A+",
            compromisedCount = 0,
            weakCount = 0,
            reusedCount = 0,
            oldPasswordCount = 0,
            missing2faCount = 0,
            issues = emptyList()
        )

        if (!isAuditDirty && cachedAuditReport != null) {
            return@withContext cachedAuditReport!!
        }

        val items = dbHelper.loadAllItems(activeVek)
        val loginItems = items.filter { it.type == ItemType.LOGIN && it.password.isNotBlank() }
        val issues = mutableListOf<SecurityIssue>()
        val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000L)

        // 1. Password reuse detection
        val passwordToItems = loginItems.groupBy { it.password }
        var reusedCount = 0
        for ((_, matchingItems) in passwordToItems) {
            if (matchingItems.size > 1) {
                reusedCount += matchingItems.size
                for (item in matchingItems) {
                    issues.add(SecurityIssue(
                        id = "reused_${item.id}",
                        itemId = item.id,
                        itemTitle = item.title,
                        itemSubtitle = item.displaySubtitle,
                        severity = IssueSeverity.WARNING,
                        type = IssueType.REUSED_PASSWORD,
                        title = "Password reused across ${matchingItems.size} accounts",
                        description = "Using the same password on multiple services creates a single point of failure.",
                        recommendation = "Generate a unique, random password for this account."
                    ))
                }
            }
        }

        // 2. Weak passwords & entropy
        var weakCount = 0
        for (item in loginItems) {
            val analysis = EntropyCalculator.analyze(item.password)
            if (analysis.strength == EntropyCalculator.StrengthScore.VERY_WEAK ||
                analysis.strength == EntropyCalculator.StrengthScore.WEAK ||
                item.password.length < 10
            ) {
                weakCount++
                issues.add(SecurityIssue(
                    id = "weak_${item.id}",
                    itemId = item.id,
                    itemTitle = item.title,
                    itemSubtitle = item.displaySubtitle,
                    severity = IssueSeverity.CRITICAL,
                    type = IssueType.WEAK_PASSWORD,
                    title = "Weak password (${analysis.entropyBits} bits entropy)",
                    description = "This password is susceptible to automated dictionary and brute-force guessing.",
                    recommendation = "Upgrade to a 20+ character password or 4-word passphrase."
                ))
            }
        }

        // 3. Old passwords
        var oldCount = 0
        for (item in loginItems) {
            if (item.updatedAt < sixMonthsAgo) {
                oldCount++
                issues.add(SecurityIssue(
                    id = "old_${item.id}",
                    itemId = item.id,
                    itemTitle = item.title,
                    itemSubtitle = item.displaySubtitle,
                    severity = IssueSeverity.INFO,
                    type = IssueType.OLD_PASSWORD,
                    title = "Password not changed in over 6 months",
                    description = "Older passwords have a higher probability of unnoticed credential leaks.",
                    recommendation = "Review and rotate credentials if necessary."
                ))
            }
        }

        // 4. Missing 2FA on high-priority domains
        var missing2faCount = 0
        val popular2faDomains = listOf(
            "google.com", "github.com", "apple.com", "amazon.com", "microsoft.com",
            "twitter.com", "x.com", "binance.com", "coinbase.com", "paypal.com", "bank"
        )
        for (item in loginItems) {
            if (item.totpSecret.isBlank()) {
                val isHighPriority = popular2faDomains.any {
                    item.website.contains(it, ignoreCase = true) || item.title.contains(it, ignoreCase = true)
                }
                if (isHighPriority) {
                    missing2faCount++
                    issues.add(SecurityIssue(
                        id = "2fa_${item.id}",
                        itemId = item.id,
                        itemTitle = item.title,
                        itemSubtitle = item.displaySubtitle,
                        severity = IssueSeverity.WARNING,
                        type = IssueType.MISSING_2FA,
                        title = "2FA / TOTP Authenticator not configured",
                        description = "This service supports multi-factor authentication for vital account security.",
                        recommendation = "Add a 2FA TOTP secret key to activate real-time login codes."
                    ))
                }
            }
        }

        // 5. Expired / Overdue Rotation Passwords
        var expiredCount = 0
        for (item in items) {
            if (item.isExpired) {
                expiredCount++
                issues.add(SecurityIssue(
                    id = "expired_${item.id}",
                    itemId = item.id,
                    itemTitle = item.title,
                    itemSubtitle = item.displaySubtitle,
                    severity = IssueSeverity.CRITICAL,
                    type = IssueType.EXPIRED_PASSWORD,
                    title = "Password rotation overdue (Expired)",
                    description = "This credential has passed its scheduled security rotation date.",
                    recommendation = "Generate a new password and update your service login."
                ))
            }
        }

        // 6. Compromised check via BreachChecker
        var compromisedCount = 0
        val isNetworkBreachEnabled = preferencesRepository?.breachCheckNetworkEnabled?.value ?: false
        for (item in loginItems) {
            val breachStatus = com.kryptx.app.core.security.BreachChecker.checkPassword(
                item.password,
                enableNetworkCheck = isNetworkBreachEnabled
            )
            if (breachStatus.isBreached) {
                compromisedCount++
                issues.add(SecurityIssue(
                    id = "comp_${item.id}",
                    itemId = item.id,
                    itemTitle = item.title,
                    itemSubtitle = item.displaySubtitle,
                    severity = IssueSeverity.CRITICAL,
                    type = IssueType.COMPROMISED,
                    title = "Known breached credential",
                    description = "This password was identified in public security breaches (${breachStatus.source}).",
                    recommendation = "Change this password immediately on the service website."
                ))
            }
        }

        // Compute overall score
        var score = 100
        score -= (compromisedCount * 30)
        score -= (expiredCount * 20)
        score -= (weakCount * 15)
        score -= (reusedCount * 8)
        score -= (oldCount * 3)
        score -= (missing2faCount * 5)
        if (loginItems.isEmpty()) score = 100
        score = score.coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "A+"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            score >= 45 -> "D"
            else -> "F"
        }

        val rawHistory = dbHelper.getSecurityScoreHistory()
        val history = rawHistory.map { SecurityScoreHistoryPoint(it.first, it.second) }

        val report = SecurityAuditReport(
            overallScore = score,
            healthGrade = grade,
            compromisedCount = compromisedCount,
            weakCount = weakCount,
            reusedCount = reusedCount,
            oldPasswordCount = oldCount,
            missing2faCount = missing2faCount,
            expiredCount = expiredCount,
            issues = issues.sortedBy { it.severity.ordinal },
            history = history
        )

        cachedAuditReport = report
        isAuditDirty = false
        dbHelper.recordSecurityScore(score)
        report
    }

    override suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val items = dbHelper.loadAllItems(activeVek)
            val salt = KeyDerivation.generateSalt()
            val derivedKey = KeyDerivation.deriveKey(exportPassword, salt)

            val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
            val ciphertext = try {
                CryptoEngine.encrypt(plaintextBytes, derivedKey)
            } finally {
                SecureMemory.wipe(plaintextBytes)
            }

            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

            SecureMemory.wipe(derivedKey)
            SecureMemory.wipe(salt)

            val header = BackupHeader(
                app = "Kryptx",
                version = "1.1.0",
                formatVersion = 1,
                exportedAt = System.currentTimeMillis(),
                isEncrypted = true,
                kdfAlgorithm = "PBKDF2WithHmacSHA256",
                kdfIterations = KeyDerivation.DEFAULT_ITERATIONS,
                saltBase64 = saltBase64,
                ivBase64 = ""
            )

            KryptxResult.Success(EncryptedBackupPayload(header, ciphertextBase64))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export encrypted backup", e)
        }
    }

    override suspend fun exportPlaintextJson(): KryptxResult<String> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val items = dbHelper.loadAllItems(activeVek)
            KryptxResult.Success(json.encodeToString(items))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export vault", e)
        }
    }

    override suspend fun importEncryptedBackup(
        payload: EncryptedBackupPayload,
        importPassword: CharArray
    ): KryptxResult<Int> = withContext(Dispatchers.Default) {
        return@withContext try {
            val salt = Base64.decode(payload.header.saltBase64, Base64.NO_WRAP)
            val derivedKey = KeyDerivation.deriveKey(importPassword, salt, payload.header.kdfIterations)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)

            val decryptedBytes = try {
                CryptoEngine.decrypt(ciphertext, derivedKey)
            } catch (e: Exception) {
                SecureMemory.wipe(derivedKey)
                return@withContext KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "Wrong import password or corrupted backup")
            }
            SecureMemory.wipe(derivedKey)

            val plaintextJson = String(decryptedBytes, Charsets.UTF_8)
            SecureMemory.wipe(decryptedBytes)

            val items = try {
                json.decodeFromString<List<VaultItem>>(plaintextJson)
            } catch (e: Exception) {
                return@withContext KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Backup file is corrupted or incompatible", e)
            }

            importItems(items)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Failed to import backup", e)
        }
    }

    override suspend fun importItems(items: List<VaultItem>): KryptxResult<Int> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val count = dbHelper.saveItemsBatch(items, activeVek)
            val report = computeSecurityAudit()
            dbHelper.recordSecurityScore(report.overallScore)
            KryptxResult.Success(count)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to import items", e)
        }
    }

    override suspend fun resetVault() = withContext(Dispatchers.IO) {
        sessionManager.lock()
        keystoreManager.removeBiometricKey()
        dbHelper.clearAllData()
    }
}
