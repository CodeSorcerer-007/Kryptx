package com.kryptx.app.core.database

import android.util.Base64
import com.kryptx.app.core.crypto.Argon2Engine
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.DestructionResult
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.core.security.IVaultSessionManager
import com.kryptx.app.core.security.withVaultKey
import com.kryptx.app.core.security.SecurityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

interface VaultAuthRepository {
    fun hasVault(): Boolean
    fun isBiometricsConfigured(): Boolean
    fun getBiometricDecryptCipher(): Cipher?
    fun getBiometricEncryptCipher(): Cipher?

    fun detectBiometricStatus(): com.kryptx.app.core.crypto.BiometricKeyStatus
    suspend fun reEnrollBiometrics(): KryptxResult<Unit>
    suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun setupBiometrics(): KryptxResult<Unit>
    suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit>
    suspend fun unlockWithBiometrics(): KryptxResult<Unit>
    suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit>
    suspend fun disableBiometrics(): KryptxResult<Unit>
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): KryptxResult<Unit>
    suspend fun rotateVaultEncryptionKey(currentMasterPassword: CharArray): KryptxResult<Unit>

    fun hasDuressPassword(): Boolean
    suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit>
    suspend fun removeDuressPassword(): KryptxResult<Unit>

    fun hasPanicPassword(): Boolean
    suspend fun setupPanicPassword(panicPassword: CharArray): KryptxResult<Unit>
    suspend fun removePanicPassword(): KryptxResult<Unit>
    suspend fun triggerPanicSelfDestruct(): DestructionResult

    fun verifyPanicCredential(candidate: CharArray): Boolean = false
    fun verifyDuressCredential(candidate: CharArray): Boolean = false
    fun verifyMasterCredential(candidate: CharArray): Boolean = false
    fun isKeyRotationRecoveryRequired(): Boolean = false

    fun isHardwareKeyEnrolled(): Boolean
    fun getHardwareKeyLabel(): String?
    fun getHardwareKeyChallenge(): ByteArray?
    fun getHardwareKeyUidHash(): String?
    suspend fun enrollHardwareKey(label: String, uidHash: String, challenge: ByteArray, hardwareSecret: ByteArray, masterPassword: CharArray): KryptxResult<Unit>
    suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun unlockWithHardwareKey(masterPassword: CharArray, hardwareSecret: ByteArray): KryptxResult<Unit>

    fun getActiveVaultId(): String
    suspend fun switchVault(vaultId: String): KryptxResult<Unit>
    suspend fun resetVault(): KryptxResult<Unit>

    fun getPqcIdentityPublicKey(): ByteArray?
    suspend fun rotatePqcIdentityKeyPair(): KryptxResult<Unit>
}

enum class RotationPhase {
    IDLE,
    STARTED,
    ITEMS_RE_ENCRYPTED,
    TOKEN_COMMITTED,
    DATABASE_REKEYED,
    COMPLETED
}

class VaultAuthRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val decoyDbHelper: KryptxDatabaseHelper,
    private val sessionManager: IVaultSessionManager,
    private val keystoreManager: KeystoreManager,
    private val onAuditInvalidated: (() -> Unit)? = null,
    private val onEmergencyWipe: (() -> DestructionResult)? = null
) : VaultAuthRepository {

    override fun hasVault(): Boolean = dbHelper.hasVaultSetup()

    override fun isBiometricsConfigured(): Boolean {
        val hasWrappedKey = !dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK).isNullOrBlank()
        return hasWrappedKey && keystoreManager.hasBiometricKey()
    }

    override fun getBiometricDecryptCipher(): Cipher? {
        return try {
            keystoreManager.getDecryptCipher()
        } catch (_: Exception) {
            null
        }
    }

    override fun getBiometricEncryptCipher(): Cipher? = keystoreManager.getEncryptCipher()

    private fun base64Decode(str: String): ByteArray {
        return try {
            val res: ByteArray? = Base64.decode(str, Base64.NO_WRAP)
            res ?: java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            val res: String? = Base64.encodeToString(bytes, Base64.NO_WRAP)
            res ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    override suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val salt = KeyDerivation.generateSalt()
        var derivedMasterKey: ByteArray? = null
        var vek: ByteArray? = null
        try {
            val argon2Params = Argon2Engine.Argon2Params.forDevice()
            derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt, argon2Params)
            vek = CryptoEngine.generateVaultKey()

            val encryptedVekPayload = CryptoEngine.encrypt(vek, derivedMasterKey)
            val tokenBase64 = base64Encode(encryptedVekPayload)
            val saltBase64 = base64Encode(salt)

            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            decoyDbHelper.setDatabaseKey(dbKey)
            SecureMemory.wipe(dbKey)

            val setupMetadata = mapOf(
                KryptxDbSchema.KEY_SALT to saltBase64,
                KryptxDbSchema.KEY_KDF_ALGORITHM to KeyDerivation.KdfAlgorithm.ARGON2ID.identifier,
                KryptxDbSchema.KEY_ARGON2_MEMORY_KB to argon2Params.memoryCostKb.toString(),
                KryptxDbSchema.KEY_ARGON2_ITERATIONS to argon2Params.iterations.toString(),
                KryptxDbSchema.KEY_ARGON2_PARALLELISM to argon2Params.parallelism.toString(),
                KryptxDbSchema.KEY_VERIFICATION_TOKEN to tokenBase64,
                KryptxDbSchema.KEY_HAS_SETUP to "true"
            )
            if (!dbHelper.setMetadataBatch(setupMetadata)) {
                throw IllegalStateException("Failed to commit vault setup metadata to secure storage")
            }

            generateAndStorePqcIdentityKeyPair(vek)

            dbHelper.recordSecurityScore(100)
            sessionManager.unlock(vek)

            onAuditInvalidated?.invoke()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            SecurityLogger.error("VaultAuthRepository", "Failed to setup new vault, rolling back initialization", e)
            val rollbackFailures = mutableListOf<String>()

            // Step 1: Session lock
            try {
                sessionManager.lock()
            } catch (t: Throwable) {
                rollbackFailures.add("session lock: ${t.message}")
            }

            // Step 2: Database clear
            try {
                if (!dbHelper.clearAllData()) {
                    rollbackFailures.add("primary database clear failed")
                }
            } catch (t: Throwable) {
                rollbackFailures.add("primary database clear: ${t.message}")
            }

            // Step 3: Decoy database clear
            try {
                if (!decoyDbHelper.clearAllData()) {
                    rollbackFailures.add("decoy database clear failed")
                }
            } catch (t: Throwable) {
                rollbackFailures.add("decoy database clear: ${t.message}")
            }

            // Step 4: Hardware Keystore wipe
            try {
                if (!keystoreManager.wipeAllKeystoreAliases()) {
                    rollbackFailures.add("keystore wipe failed")
                }
            } catch (t: Throwable) {
                rollbackFailures.add("keystore wipe: ${t.message}")
            }

            val errorMsg = if (rollbackFailures.isEmpty()) {
                "Failed to create vault: ${e.message}"
            } else {
                "Failed to create vault: ${e.message}. Rollback incomplete: ${rollbackFailures.joinToString("; ")}"
            }
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, errorMsg, e)
        } finally {
            SecureMemory.wipe(vek)
            SecureMemory.wipe(derivedMasterKey)
            SecureMemory.wipe(salt)
        }
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        if (sessionManager.lockoutSecondsRemaining.value > 0) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.RATE_LIMITED,
                "Too many failed unlock attempts. Please wait ${sessionManager.lockoutSecondsRemaining.value}s."
            )
        }
        if (isKeyRotationRecoveryRequired()) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.DATABASE_ERROR,
                "Vault key rotation was interrupted or failed rollback. Vault recovery required."
            )
        }
        if (hasPanicPassword() && verifyPanicCredential(masterPassword)) {
            val destructResult = triggerPanicSelfDestruct()
            return@withContext if (destructResult is DestructionResult.Success) {
                KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault has been wiped via panic self-destruct.")
            } else {
                val errMsg = (destructResult as? DestructionResult.Incomplete)?.errorMessage ?: "Incomplete panic destruction"
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Panic wipe incomplete: $errMsg")
            }
        }

        val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)

        if (saltBase64 != null && tokenBase64 != null) {
            val salt = base64Decode(saltBase64)
            val tokenBytes = base64Decode(tokenBase64)

            val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)
            val memoryKb = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_MEMORY_KB)?.toIntOrNull()
            val iterations = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_ITERATIONS)?.toIntOrNull()
            val parallelism = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_PARALLELISM)?.toIntOrNull()
            val argon2Params = if (memoryKb != null && iterations != null && parallelism != null) {
                Argon2Engine.Argon2Params(
                    memoryCostKb = memoryKb,
                    iterations = iterations,
                    parallelism = parallelism,
                    outputLength = 32
                )
            } else {
                Argon2Engine.Argon2Params.forDevice()
            }

            val derivedMasterKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(masterPassword, salt, argon2Params)
            } else {
                KeyDerivation.deriveKey(masterPassword, salt)
            }

            var vek: ByteArray? = null
            var success = false
            try {
                vek = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)

                val dbKey = deriveSqlCipherKey(vek)
                dbHelper.setDatabaseKey(dbKey)
                SecureMemory.wipe(dbKey)

                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.withVaultKey { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                success = true
                if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier && memoryKb == null) {
                    dbHelper.setMetadataBatch(
                        mapOf(
                            KryptxDbSchema.KEY_ARGON2_MEMORY_KB to argon2Params.memoryCostKb.toString(),
                            KryptxDbSchema.KEY_ARGON2_ITERATIONS to argon2Params.iterations.toString(),
                            KryptxDbSchema.KEY_ARGON2_PARALLELISM to argon2Params.parallelism.toString()
                        )
                    )
                }

                // Seamlessly migrate legacy PBKDF2 vaults to memory-hard Argon2id upon successful authentication
                if (kdfAlgorithm != KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                    try {
                        val newSalt = KeyDerivation.generateSalt()
                        val newParams = Argon2Engine.Argon2Params.forDevice()
                        val newDerivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, newSalt, newParams)
                        val newEncryptedVekPayload = CryptoEngine.encrypt(vek, newDerivedMasterKey)
                        val migrationBatch = mapOf(
                            KryptxDbSchema.KEY_SALT to base64Encode(newSalt),
                            KryptxDbSchema.KEY_VERIFICATION_TOKEN to base64Encode(newEncryptedVekPayload),
                            KryptxDbSchema.KEY_KDF_ALGORITHM to KeyDerivation.KdfAlgorithm.ARGON2ID.identifier,
                            KryptxDbSchema.KEY_ARGON2_MEMORY_KB to newParams.memoryCostKb.toString(),
                            KryptxDbSchema.KEY_ARGON2_ITERATIONS to newParams.iterations.toString(),
                            KryptxDbSchema.KEY_ARGON2_PARALLELISM to newParams.parallelism.toString()
                        )
                        val migrated = dbHelper.setMetadataBatch(migrationBatch)
                        SecureMemory.wipe(newDerivedMasterKey)
                        SecureMemory.wipe(newSalt)
                        if (migrated) {
                            SecurityLogger.info("VaultAuthRepository", "Legacy PBKDF2 vault successfully auto-migrated to Argon2id")
                        } else {
                            SecurityLogger.warn("VaultAuthRepository", "Failed to commit Argon2id migration metadata batch; retaining existing PBKDF2 configuration")
                        }
                    } catch (migrationEx: Exception) {
                        SecurityLogger.warn("VaultAuthRepository", "Failed to auto-migrate legacy vault to Argon2id", migrationEx)
                    }
                }

                // If biometric was previously configured, auto-re-enroll now that we have the active VEK to guarantee cryptographic alignment!
                val hasBiometricWrapped = !dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK).isNullOrBlank()
                if (hasBiometricWrapped) {
                    try {
                        val oldKeyRemoved = keystoreManager.removeBiometricKey()
                        if (!oldKeyRemoved) {
                            SecurityLogger.warn("VaultAuthRepository", "Failed to remove old biometric key during auto-re-enrollment; aborting biometric refresh")
                        } else {
                            val newWrappedVek = keystoreManager.wrapWithPublicKey(vek)
                            val bioBatch = mapOf(
                                KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK to base64Encode(newWrappedVek),
                                KryptxDbSchema.KEY_BIOMETRIC_IV to "rsa_oaep"
                            )
                            val committed = dbHelper.setMetadataBatch(bioBatch)
                            if (committed) {
                                SecurityLogger.info("VaultAuthRepository", "Biometric key auto-re-enrolled following master password unlock")
                            } else {
                                SecurityLogger.warn("VaultAuthRepository", "Failed to commit auto-re-enrolled biometric metadata")
                            }
                        }
                    } catch (e: Exception) {
                        SecurityLogger.warn("VaultAuthRepository", "Failed to auto-re-enroll biometric key", e)
                    }
                }

                onAuditInvalidated?.invoke()
            } catch (e: Exception) {
                // Decryption failure is expected when the password is wrong — this is a normal fallthrough.
                // Trace-log to aid diagnostics without exposing details at higher severity.
                SecurityLogger.trace("VaultAuthRepository", "Master vault decrypt attempt failed (wrong password or corrupted token)", e)
            } finally {
                SecureMemory.wipe(vek)
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(salt)
            }

            if (success) {
                return@withContext KryptxResult.Success(Unit)
            }
        }

        if (hasDuressPassword()) {
            val duressSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_SALT)
            val duressTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_TOKEN)

            if (duressSaltBase64 != null && duressTokenBase64 != null) {
                val duressSalt = base64Decode(duressSaltBase64)
                val duressTokenBytes = base64Decode(duressTokenBase64)
                val derivedDuressKey = KeyDerivation.deriveKeyArgon2(masterPassword, duressSalt)

                var decoyVek: ByteArray? = null
                var duressSuccess = false
                try {
                    decoyVek = CryptoEngine.decrypt(duressTokenBytes, derivedDuressKey)

                    val dbKey = deriveSqlCipherKey(decoyVek)
                    decoyDbHelper.setDatabaseKey(dbKey)
                    SecureMemory.wipe(dbKey)

                    sessionManager.unlock(decoyVek, isDecoy = true)
                    decoyDbHelper.loadAllItems(decoyVek)
                    duressSuccess = true
                    onAuditInvalidated?.invoke()
                } catch (e: Exception) {
                    // Decryption failure is expected when password doesn't match duress vault.
                    SecurityLogger.trace("VaultAuthRepository", "Duress vault decrypt attempt failed (expected on non-duress unlock)", e)
                } finally {
                    SecureMemory.wipe(decoyVek)
                    SecureMemory.wipe(derivedDuressKey)
                    SecureMemory.wipe(duressSalt)
                }

                if (duressSuccess) {
                    return@withContext KryptxResult.Success(Unit)
                }
            }
        }

        sessionManager.recordFailedAttempt()
        KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect master password")
    }

    override suspend fun setupBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                keystoreManager.removeBiometricKey()
                val wrappedVek = keystoreManager.wrapWithPublicKey(activeVek)
                val wrappedBase64 = base64Encode(wrappedVek)

                val bioBatch = mapOf(
                    KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK to wrappedBase64,
                    KryptxDbSchema.KEY_BIOMETRIC_IV to "rsa_oaep"
                )
                if (!dbHelper.setMetadataBatch(bioBatch)) {
                    SecurityLogger.error("VaultAuthRepository", "Failed to persist biometric metadata batch to secure storage")
                    return@withVaultKey KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Failed to persist biometric metadata to secure storage"
                    )
                }
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Failed to enroll biometric key", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val (wrappedVek, _) = keystoreManager.wrapWithCipher(cipher, activeVek)
                val wrappedBase64 = base64Encode(wrappedVek)

                val bioBatch = mapOf(
                    KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK to wrappedBase64,
                    KryptxDbSchema.KEY_BIOMETRIC_IV to "rsa_oaep"
                )
                if (!dbHelper.setMetadataBatch(bioBatch)) {
                    SecurityLogger.error("VaultAuthRepository", "Failed to persist biometric metadata batch to secure storage")
                    return@withVaultKey KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Failed to persist biometric metadata to secure storage"
                    )
                }
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Failed to enroll biometric key with cipher", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun detectBiometricStatus(): com.kryptx.app.core.crypto.BiometricKeyStatus {
        return keystoreManager.detectBiometricInvalidation()
    }

    override suspend fun reEnrollBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                keystoreManager.removeBiometricKey()
                val wrappedVek = keystoreManager.wrapWithPublicKey(activeVek)
                val wrappedBase64 = base64Encode(wrappedVek)
                val bioBatch = mapOf(
                    KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK to wrappedBase64,
                    KryptxDbSchema.KEY_BIOMETRIC_IV to "rsa_oaep"
                )
                if (!dbHelper.setMetadataBatch(bioBatch)) {
                    SecurityLogger.error("VaultAuthRepository", "Failed to persist biometric metadata batch to secure storage")
                    return@withVaultKey KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Failed to persist biometric metadata to secure storage"
                    )
                }
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Failed to re-enroll biometric key", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    @Deprecated("Hardware biometric key requires user authentication via BiometricPrompt. Use unlockWithBiometricCipher(cipher).")
    override suspend fun unlockWithBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        KryptxResult.Error(
            KryptxErrorType.BIOMETRICS_FAILED,
            "Biometric unlock requires hardware authentication through BiometricPrompt. Use unlockWithBiometricCipher."
        )
    }

    override suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val wrappedBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "No biometric key stored")
        var vek: ByteArray? = null
        try {
            val wrappedBytes = base64Decode(wrappedBase64)
            vek = keystoreManager.unwrapWithCipher(cipher, wrappedBytes)

            val dbKey = deriveSqlCipherKey(vek)
            try {
                dbHelper.setDatabaseKey(dbKey)
            } finally {
                SecureMemory.wipe(dbKey)
            }

            sessionManager.unlock(vek)
            sessionManager.withVaultKey { activeKey ->
                dbHelper.loadAllItems(activeKey)
            }
            onAuditInvalidated?.invoke()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            sessionManager.recordFailedAttempt()
            com.kryptx.app.core.security.SecurityLogger.warn("VaultAuthRepository", "Biometric decryption failed: ${e.javaClass.name}: ${e.message}", e)
            val isPaddingOrKeyError = e is javax.crypto.BadPaddingException || e is java.security.InvalidKeyException
            val message = if (isPaddingOrKeyError) {
                "Biometric key updated. Please unlock once with Master Password to re-sync biometrics."
            } else {
                "Biometric authentication failed. Please use master password."
            }
            KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, message, e)
        } finally {
            vek?.let { SecureMemory.wipe(it) }
        }
    }

    override suspend fun disableBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val removed = try {
            keystoreManager.removeBiometricKey()
        } catch (e: Exception) {
            SecurityLogger.warn("VaultAuthRepository", "Exception during biometric key removal", e)
            false
        }
        if (!removed) {
            SecurityLogger.error("VaultAuthRepository", "Failed to delete biometric key alias from Keystore during disableBiometrics")
            return@withContext KryptxResult.Error(
                KryptxErrorType.BIOMETRICS_FAILED,
                "Failed to delete biometric key from Android Keystore. Biometrics cannot be safely disabled while hardware alias remains."
            )
        }
        val bioBatch = mapOf(
            KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK to "",
            KryptxDbSchema.KEY_BIOMETRIC_IV to ""
        )
        if (!dbHelper.setMetadataBatch(bioBatch)) {
            SecurityLogger.error(
                "VaultAuthRepository",
                "Failed to commit metadata clearing during disableBiometrics"
            )
            return@withContext KryptxResult.Error(
                KryptxErrorType.DATABASE_ERROR,
                "Biometric key alias deleted from Keystore, but failed to persist metadata updates to secure storage."
            )
        }
        KryptxResult.Success(Unit)
    }

    override suspend fun changeMasterPassword(
        currentPassword: CharArray,
        newPassword: CharArray
    ): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
                ?: return@withVaultKey KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault salt not found")
            val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)
                ?: return@withVaultKey KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault token not found")

            val salt = base64Decode(saltBase64)
            val tokenBytes = base64Decode(tokenBase64)
            val currentDerivedKey = deriveMasterKeyForVault(currentPassword, salt)

            val verifiedVek = try {
                CryptoEngine.decrypt(tokenBytes, currentDerivedKey)
            } catch (e: Exception) {
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
                return@withVaultKey KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect current master password")
            }
            SecureMemory.wipe(currentDerivedKey)
            SecureMemory.wipe(salt)
            SecureMemory.wipe(verifiedVek)

            if (verifyDuressCredential(newPassword)) {
                return@withVaultKey KryptxResult.Error(
                    KryptxErrorType.WRONG_PASSWORD,
                    "New master password cannot be identical to your Duress PIN"
                )
            }
            if (verifyPanicCredential(newPassword)) {
                return@withVaultKey KryptxResult.Error(
                    KryptxErrorType.WRONG_PASSWORD,
                    "New master password cannot be identical to your Panic PIN"
                )
            }

            val newSalt = KeyDerivation.generateSalt()
            val newParams = Argon2Engine.Argon2Params.forDevice()
            val newDerivedKey = KeyDerivation.deriveKeyArgon2(newPassword, newSalt, newParams)
            val newEncryptedVek = CryptoEngine.encrypt(activeVek, newDerivedKey)

            val newMetadataBatch = mapOf(
                KryptxDbSchema.KEY_SALT to base64Encode(newSalt),
                KryptxDbSchema.KEY_VERIFICATION_TOKEN to base64Encode(newEncryptedVek),
                KryptxDbSchema.KEY_KDF_ALGORITHM to KeyDerivation.KdfAlgorithm.ARGON2ID.identifier,
                KryptxDbSchema.KEY_ARGON2_MEMORY_KB to newParams.memoryCostKb.toString(),
                KryptxDbSchema.KEY_ARGON2_ITERATIONS to newParams.iterations.toString(),
                KryptxDbSchema.KEY_ARGON2_PARALLELISM to newParams.parallelism.toString()
            )
            if (!dbHelper.setMetadataBatch(newMetadataBatch)) {
                SecureMemory.wipe(newDerivedKey)
                SecureMemory.wipe(newSalt)
                return@withVaultKey KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to commit changed master password metadata")
            }

            if (isBiometricsConfigured()) {
                setupBiometrics()
            }

            SecureMemory.wipe(newDerivedKey)
            SecureMemory.wipe(newSalt)
            KryptxResult.Success(Unit)
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun rotateVaultEncryptionKey(currentMasterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
                ?: return@withVaultKey KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault salt not found")
            val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)
                ?: return@withVaultKey KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault token not found")

            val salt = base64Decode(saltBase64)
            val tokenBytes = base64Decode(tokenBase64)
            val currentDerivedKey = deriveMasterKeyForVault(currentMasterPassword, salt)

            val verifiedVek = try {
                CryptoEngine.decrypt(tokenBytes, currentDerivedKey)
            } catch (_: Exception) {
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
                return@withVaultKey KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect master password")
            }
            SecureMemory.wipe(verifiedVek)

            val newVek = CryptoEngine.generateVaultKey()
            var currentPhase = RotationPhase.IDLE

            try {
                // Initialize durable journal before performing any irreversible mutation
                val journalBatch = mapOf(
                    KryptxDbSchema.KEY_ROTATION_PHASE to RotationPhase.STARTED.name,
                    KryptxDbSchema.KEY_ROTATION_OLD_TOKEN to tokenBase64,
                    KryptxDbSchema.KEY_ROTATION_TIMESTAMP to System.currentTimeMillis().toString(),
                    KryptxDbSchema.KEY_ROTATION_RECOVERY_REQUIRED to "false"
                )
                if (!dbHelper.setMetadataBatch(journalBatch)) {
                    throw IllegalStateException("Failed to initialize key rotation journal in secure storage")
                }
                currentPhase = RotationPhase.STARTED

                // Step 1: Re-encrypt all vault items under new VEK (transactional SQL)
                dbHelper.reEncryptVaultWithNewKey(activeVek, newVek)
                dbHelper.setMetadata(KryptxDbSchema.KEY_ROTATION_PHASE, RotationPhase.ITEMS_RE_ENCRYPTED.name)
                currentPhase = RotationPhase.ITEMS_RE_ENCRYPTED

                // Step 2: Encrypt new VEK under master password and update verification token
                val newEncryptedVek = CryptoEngine.encrypt(newVek, currentDerivedKey)
                val tokenSaved = dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, base64Encode(newEncryptedVek))
                if (!tokenSaved) {
                    throw IllegalStateException("Failed to commit rotated verification token to secure storage")
                }
                dbHelper.setMetadata(KryptxDbSchema.KEY_ROTATION_PHASE, RotationPhase.TOKEN_COMMITTED.name)
                currentPhase = RotationPhase.TOKEN_COMMITTED

                // Step 3: Rekey SQLCipher database file encryption to match new VEK (PRAGMA rekey)
                val newDbKey = deriveSqlCipherKey(newVek)
                try {
                    dbHelper.rekeyDatabase(newDbKey)
                } finally {
                    SecureMemory.wipe(newDbKey)
                }
                dbHelper.setMetadata(KryptxDbSchema.KEY_ROTATION_PHASE, RotationPhase.DATABASE_REKEYED.name)
                currentPhase = RotationPhase.DATABASE_REKEYED

                // Step 4: Swap active session key
                sessionManager.unlock(newVek, isDecoy = false)

                // Re-encrypt PQC identity key under new VEK
                try {
                    generateAndStorePqcIdentityKeyPair(newVek)
                } catch (pqcEx: Exception) {
                    SecurityLogger.warn("VaultAuthRepository", "Failed to re-encrypt PQC identity key under new VEK", pqcEx)
                }

                if (isBiometricsConfigured()) {
                    setupBiometrics()
                }

                // Step 5: Clean up journal
                dbHelper.removeMetadataBatch(
                    listOf(
                        KryptxDbSchema.KEY_ROTATION_PHASE,
                        KryptxDbSchema.KEY_ROTATION_OLD_TOKEN,
                        KryptxDbSchema.KEY_ROTATION_TIMESTAMP
                    )
                )

                onAuditInvalidated?.invoke()
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                SecurityLogger.error("VaultAuthRepository", "Key rotation failed during phase $currentPhase, executing phase-aware rollback", e)
                val rollbackFailures = mutableListOf<String>()

                // Rollback Step 3: If database was rekeyed, revert SQLCipher page encryption
                if (currentPhase >= RotationPhase.DATABASE_REKEYED) {
                    val oldDbKey = deriveSqlCipherKey(activeVek)
                    try {
                        dbHelper.rekeyDatabase(oldDbKey)
                    } catch (rollbackRekeyEx: Exception) {
                        rollbackFailures.add("Database rekey rollback failed: ${rollbackRekeyEx.message}")
                        SecurityLogger.error("VaultAuthRepository", "Rollback rekeyDatabase failed", rollbackRekeyEx)
                    } finally {
                        SecureMemory.wipe(oldDbKey)
                    }
                }

                // Rollback Step 2: If token was committed, restore old token and verify
                if (currentPhase >= RotationPhase.TOKEN_COMMITTED) {
                    val tokenRestored = dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, tokenBase64)
                    if (!tokenRestored) {
                        rollbackFailures.add("Verification token restore failed")
                        SecurityLogger.error("VaultAuthRepository", "Rollback verification token failed")
                    }
                }

                // Rollback Step 1: If items were re-encrypted, re-encrypt items back to activeVek
                if (currentPhase >= RotationPhase.ITEMS_RE_ENCRYPTED) {
                    try {
                        dbHelper.reEncryptVaultWithNewKey(newVek, activeVek)
                    } catch (rollbackReEncryptEx: Exception) {
                        rollbackFailures.add("Item re-encryption rollback failed: ${rollbackReEncryptEx.message}")
                        SecurityLogger.error("VaultAuthRepository", "Rollback reEncryptVaultWithNewKey failed", rollbackReEncryptEx)
                    }
                }

                if (rollbackFailures.isNotEmpty()) {
                    // Mark recovery required in metadata so future unlocks and operations fail closed safely
                    dbHelper.setMetadata(KryptxDbSchema.KEY_ROTATION_RECOVERY_REQUIRED, "true")
                    SecurityLogger.error(
                        "VaultAuthRepository",
                        "CRITICAL: Key rotation rollback failed: ${rollbackFailures.joinToString("; ")}. Recovery flag set."
                    )
                    KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Key rotation failed and rollback was incomplete (${rollbackFailures.joinToString("; ")}). Vault recovery required.",
                        e
                    )
                } else {
                    // Rollback was completely clean: remove the transient journal entries
                    dbHelper.removeMetadataBatch(
                        listOf(
                            KryptxDbSchema.KEY_ROTATION_PHASE,
                            KryptxDbSchema.KEY_ROTATION_OLD_TOKEN,
                            KryptxDbSchema.KEY_ROTATION_TIMESTAMP
                        )
                    )
                    KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Failed to rotate encryption key. All changes have been safely rolled back.",
                        e
                    )
                }
            } finally {
                SecureMemory.wipe(newVek)
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun hasDuressPassword(): Boolean = dbHelper.hasDuressSetup()

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        if (verifyMasterCredential(duressPassword)) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.WRONG_PASSWORD,
                "Duress PIN cannot be identical to your master password"
            )
        }

        if (verifyPanicCredential(duressPassword)) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.WRONG_PASSWORD,
                "Duress PIN cannot be identical to your Panic PIN"
            )
        }

        var salt: ByteArray? = null
        var derivedDuressKey: ByteArray? = null
        var decoyVek: ByteArray? = null
        var decoyDbKey: ByteArray? = null

        try {
            salt = KeyDerivation.generateSalt()
            derivedDuressKey = KeyDerivation.deriveKeyArgon2(duressPassword, salt)
            decoyVek = CryptoEngine.generateVaultKey()
            val encryptedDecoyPayload = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

            val tokenBase64 = base64Encode(encryptedDecoyPayload)
            val saltBase64 = base64Encode(salt)

            // Step 1: Stage decoy database key and provision items BEFORE committing metadata
            decoyDbKey = deriveSqlCipherKey(decoyVek)
            decoyDbHelper.setDatabaseKey(decoyDbKey)
            decoyDbHelper.provisionDefaultItems(decoyVek)

            // Step 2: Commit metadata batch atomically
            val duressBatch = mapOf(
                KryptxDbSchema.KEY_DURESS_SALT to saltBase64,
                KryptxDbSchema.KEY_DURESS_TOKEN to tokenBase64,
                KryptxDbSchema.KEY_HAS_DURESS to "true"
            )
            if (!dbHelper.setMetadataBatch(duressBatch)) {
                throw IllegalStateException("Failed to commit duress vault metadata to secure storage")
            }

            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            SecurityLogger.error("VaultAuthRepository", "Failed to setup duress vault, rolling back decoy database and metadata", e)
            try {
                decoyDbHelper.clearAllData()
            } catch (clearEx: Exception) {
                SecurityLogger.warn("VaultAuthRepository", "Failed to clear decoy DB during rollback", clearEx)
            }
            try {
                dbHelper.removeMetadataBatch(
                    listOf(
                        KryptxDbSchema.KEY_DURESS_SALT,
                        KryptxDbSchema.KEY_DURESS_TOKEN,
                        KryptxDbSchema.KEY_HAS_DURESS
                    )
                )
            } catch (metaEx: Exception) {
                SecurityLogger.warn("VaultAuthRepository", "Failed to clear duress metadata during rollback", metaEx)
            }
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup duress vault: ${e.message}", e)
        } finally {
            SecureMemory.wipe(decoyDbKey)
            SecureMemory.wipe(decoyVek)
            SecureMemory.wipe(derivedDuressKey)
            SecureMemory.wipe(salt)
        }
    }

    override suspend fun removeDuressPassword(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val duressBatch = mapOf(
            KryptxDbSchema.KEY_DURESS_SALT to "",
            KryptxDbSchema.KEY_DURESS_TOKEN to "",
            KryptxDbSchema.KEY_HAS_DURESS to "false"
        )
        if (!dbHelper.setMetadataBatch(duressBatch)) {
            SecurityLogger.warn("VaultAuthRepository", "Failed to commit duress password removal metadata")
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove duress password from secure storage")
        } else {
            KryptxResult.Success(Unit)
        }
    }

    override fun hasPanicPassword(): Boolean = dbHelper.getMetadata(KryptxDbSchema.KEY_HAS_PANIC) == "true"

    override suspend fun setupPanicPassword(panicPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        if (verifyMasterCredential(panicPassword)) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.WRONG_PASSWORD,
                "Panic PIN cannot be identical to your master password"
            )
        }

        if (verifyDuressCredential(panicPassword)) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.WRONG_PASSWORD,
                "Panic PIN cannot be identical to your Duress PIN"
            )
        }

        var salt: ByteArray? = null
        var derivedKey: ByteArray? = null
        var panicVerificationToken: ByteArray? = null

        try {
            salt = KeyDerivation.generateSalt()
            derivedKey = KeyDerivation.deriveKeyArgon2(panicPassword, salt)
            panicVerificationToken = CryptoEngine.generateVaultKey()
            val encryptedToken = CryptoEngine.encrypt(
                panicVerificationToken,
                derivedKey,
                "kryptx-panic-auth".toByteArray(Charsets.UTF_8)
            )

            val panicBatch = mapOf(
                KryptxDbSchema.KEY_PANIC_SALT to base64Encode(salt),
                KryptxDbSchema.KEY_PANIC_TOKEN to base64Encode(encryptedToken),
                KryptxDbSchema.KEY_PANIC_HASH to "",
                KryptxDbSchema.KEY_HAS_PANIC to "true",
                KryptxDbSchema.KEY_PANIC_KDF_ALGORITHM to KeyDerivation.KdfAlgorithm.ARGON2ID.identifier
            )
            if (!dbHelper.setMetadataBatch(panicBatch)) {
                throw IllegalStateException("Failed to commit panic password metadata to secure storage")
            }

            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            SecurityLogger.error("VaultAuthRepository", "Failed to setup panic password, rolling back", e)
            try {
                dbHelper.removeMetadataBatch(
                    listOf(
                        KryptxDbSchema.KEY_PANIC_SALT,
                        KryptxDbSchema.KEY_PANIC_TOKEN,
                        KryptxDbSchema.KEY_PANIC_HASH,
                        KryptxDbSchema.KEY_HAS_PANIC,
                        KryptxDbSchema.KEY_PANIC_KDF_ALGORITHM
                    )
                )
            } catch (metaEx: Exception) {
                SecurityLogger.warn("VaultAuthRepository", "Failed to clear panic metadata during rollback", metaEx)
            }
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup panic password: ${e.message}", e)
        } finally {
            SecureMemory.wipe(panicVerificationToken)
            SecureMemory.wipe(derivedKey)
            SecureMemory.wipe(salt)
        }
    }

    override suspend fun removePanicPassword(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val panicBatch = mapOf(
            KryptxDbSchema.KEY_PANIC_SALT to "",
            KryptxDbSchema.KEY_PANIC_TOKEN to "",
            KryptxDbSchema.KEY_PANIC_HASH to "",
            KryptxDbSchema.KEY_HAS_PANIC to "false",
            KryptxDbSchema.KEY_PANIC_KDF_ALGORITHM to ""
        )
        if (!dbHelper.setMetadataBatch(panicBatch)) {
            SecurityLogger.warn("VaultAuthRepository", "Failed to commit panic password removal metadata")
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove panic password from secure storage")
        } else {
            KryptxResult.Success(Unit)
        }
    }

    override fun isKeyRotationRecoveryRequired(): Boolean =
        dbHelper.getMetadata(KryptxDbSchema.KEY_ROTATION_RECOVERY_REQUIRED) == "true"

    override fun verifyPanicCredential(candidate: CharArray): Boolean {
        if (!hasPanicPassword()) return false
        val panicSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PANIC_SALT) ?: return false
        val panicTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PANIC_TOKEN)
        val panicHashBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PANIC_HASH)
        val panicKdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_PANIC_KDF_ALGORITHM)

        if (panicTokenBase64.isNullOrBlank() && panicHashBase64.isNullOrBlank()) return false

        var panicSalt: ByteArray? = null
        var derivedPanicKey: ByteArray? = null
        try {
            panicSalt = base64Decode(panicSaltBase64)
            derivedPanicKey = if (panicKdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(candidate, panicSalt)
            } else {
                KeyDerivation.deriveKey(candidate, panicSalt)
            }

            if (!panicTokenBase64.isNullOrBlank()) {
                try {
                    val tokenBytes = base64Decode(panicTokenBase64)
                    val decrypted = CryptoEngine.decrypt(
                        tokenBytes,
                        derivedPanicKey,
                        "kryptx-panic-auth".toByteArray(Charsets.UTF_8)
                    )
                    SecureMemory.wipe(decrypted)
                    return true
                } catch (_: Exception) {
                    return false
                }
            } else if (!panicHashBase64.isNullOrBlank()) {
                try {
                    val expectedHash = base64Decode(panicHashBase64)
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val actualHash = md.digest(derivedPanicKey)
                    return SecureMemory.safeEquals(actualHash, expectedHash)
                } catch (_: Exception) {
                    return false
                }
            }
            return false
        } catch (_: Exception) {
            return false
        } finally {
            SecureMemory.wipe(derivedPanicKey)
            SecureMemory.wipe(panicSalt)
        }
    }

    override fun verifyDuressCredential(candidate: CharArray): Boolean {
        if (!hasDuressPassword()) return false
        val duressSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_SALT) ?: return false
        val duressTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_TOKEN) ?: return false

        var duressSalt: ByteArray? = null
        var derivedDuressKey: ByteArray? = null
        try {
            duressSalt = base64Decode(duressSaltBase64)
            val duressTokenBytes = base64Decode(duressTokenBase64)
            derivedDuressKey = KeyDerivation.deriveKeyArgon2(candidate, duressSalt)
            val decrypted = CryptoEngine.decrypt(duressTokenBytes, derivedDuressKey)
            SecureMemory.wipe(decrypted)
            return true
        } catch (_: Exception) {
            return false
        } finally {
            SecureMemory.wipe(derivedDuressKey)
            SecureMemory.wipe(duressSalt)
        }
    }

    override fun verifyMasterCredential(candidate: CharArray): Boolean {
        val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT) ?: return false
        val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN) ?: return false

        var salt: ByteArray? = null
        var derivedMasterKey: ByteArray? = null
        try {
            salt = base64Decode(saltBase64)
            val tokenBytes = base64Decode(tokenBase64)
            derivedMasterKey = deriveMasterKeyForVault(candidate, salt)
            val decrypted = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)
            SecureMemory.wipe(decrypted)
            return true
        } catch (_: Exception) {
            return false
        } finally {
            SecureMemory.wipe(derivedMasterKey)
            SecureMemory.wipe(salt)
        }
    }

    override suspend fun triggerPanicSelfDestruct(): DestructionResult = withContext(Dispatchers.Default) {
        // Deliberately uses Dispatchers.Default (not IO) to be consistent with all other crypto-touching
        // operations. The file deletions here are fast destructive writes, not large-file IO.
        // 1. Instantaneous zeroization of in-memory active vault key buffer
        sessionManager.lock()

        // 2. Cryptographic erasure: Purge all Android Keystore aliases (biometric, ECDH, metadata MasterKey)
        val keystorePurged = keystoreManager.wipeAllKeystoreAliases()

        // 3. Clear database tables, preferences, companion sidecars (-wal, -shm, -journal), and attachment files
        val primaryDbPurged = dbHelper.clearAllData()
        val decoyDbPurged = decoyDbHelper.clearAllData()
        val databasesPurged = primaryDbPurged && decoyDbPurged

        // 4. Central emergency self-destruct pipeline execution (multi-pass file overwrite & shred)
        val emergencyResult = onEmergencyWipe?.invoke() ?: DestructionResult.Incomplete(
            keystorePurged = keystorePurged,
            databasesPurged = false,
            preferencesPurged = false,
            errorMessage = "Central emergency wipe pipeline callback was not configured"
        )

        val finalResult = if (emergencyResult is DestructionResult.Incomplete) {
            emergencyResult.copy(
                keystorePurged = keystorePurged && emergencyResult.keystorePurged,
                databasesPurged = databasesPurged && emergencyResult.databasesPurged
            )
        } else if (!keystorePurged || !databasesPurged) {
            val failureReasons = mutableListOf<String>()
            if (!keystorePurged) failureReasons.add("keystore purge failed")
            if (!primaryDbPurged) failureReasons.add("primary database purge failed")
            if (!decoyDbPurged) failureReasons.add("decoy database purge failed")

            DestructionResult.Incomplete(
                keystorePurged = keystorePurged,
                databasesPurged = databasesPurged,
                preferencesPurged = true,
                errorMessage = "Cryptographic erasure postcondition failed: ${failureReasons.joinToString("; ")} during panic destruct"
            )
        } else {
            DestructionResult.Success
        }

        if (finalResult is DestructionResult.Incomplete) {
            SecurityLogger.error(
                "VaultAuthRepository",
                "Panic self-destruct completed with incomplete status: ${finalResult.errorMessage}"
            )
        }
        finalResult
    }

    override fun isHardwareKeyEnrolled(): Boolean {
        return dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED) == "true" &&
                !dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE).isNullOrBlank()
    }

    override fun getHardwareKeyLabel(): String? = dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL)

    override fun getHardwareKeyChallenge(): ByteArray? {
        val challengeBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE) ?: return null
        return try {
            base64Decode(challengeBase64)
        } catch (_: Exception) {
            null
        }
    }

    override fun getHardwareKeyUidHash(): String? = dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH)

    override suspend fun enrollHardwareKey(
        label: String,
        uidHash: String,
        challenge: ByteArray,
        hardwareSecret: ByteArray,
        masterPassword: CharArray
    ): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            var baseSalt: ByteArray? = null
            var combinedSalt: ByteArray? = null
            var argon2Salt: ByteArray? = null
            var derivedKey: ByteArray? = null

            try {
                if (!verifyMasterCredential(masterPassword)) {
                    return@withVaultKey KryptxResult.Error(
                        KryptxErrorType.WRONG_PASSWORD,
                        "Incorrect master password for hardware key enrollment"
                    )
                }

                baseSalt = KeyDerivation.generateSalt()
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.update(baseSalt)
                md.update(hardwareSecret)
                combinedSalt = md.digest()

                // Extend combinedSalt to 32 bytes for Argon2id minimum salt requirement
                argon2Salt = combinedSalt.copyOf(32)
                // Use Argon2id consistently with master password KDF — hardware key should
                // have equal or greater brute-force resistance than the primary unlock path
                derivedKey = KeyDerivation.deriveKeyArgon2(masterPassword, argon2Salt)
                val encryptedVekPayload = CryptoEngine.encrypt(activeVek, derivedKey)

                val tokenBase64 = base64Encode(encryptedVekPayload)
                val saltBase64 = base64Encode(baseSalt)
                val challengeBase64 = base64Encode(challenge)

                // Write to HW-specific keys — never overwrite the master password's KEY_SALT /
                // KEY_VERIFICATION_TOKEN so the password-only unlock path remains intact as a
                // permanent recovery option even after hardware key enrollment.
                val hwBatch = mapOf(
                    KryptxDbSchema.KEY_HW_SALT to saltBase64,
                    KryptxDbSchema.KEY_HW_VERIFICATION_TOKEN to tokenBase64,
                    KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED to "true",
                    KryptxDbSchema.KEY_HARDWARE_KEY_LABEL to label,
                    KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH to uidHash,
                    KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE to challengeBase64
                )
                if (!dbHelper.setMetadataBatch(hwBatch)) {
                    throw IllegalStateException("Failed to commit hardware security key metadata batch")
                }

                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                SecurityLogger.error("VaultAuthRepository", "Failed to enroll hardware security key", e)
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to enroll hardware security key: ${e.message}", e)
            } finally {
                SecureMemory.wipe(derivedKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
                SecureMemory.wipe(argon2Salt)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to enroll hardware key")
    }

    override suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { _ ->
            if (!verifyMasterCredential(masterPassword)) {
                return@withVaultKey KryptxResult.Error(
                    KryptxErrorType.WRONG_PASSWORD,
                    "Incorrect master password to remove hardware security key"
                )
            }
            try {
                // Clear the hardware-key-specific token — the master password token
                // (KEY_SALT / KEY_VERIFICATION_TOKEN) was never overwritten during enrollment
                // and remains valid for password-only unlock.
                val hwBatch = mapOf(
                    KryptxDbSchema.KEY_HW_SALT to "",
                    KryptxDbSchema.KEY_HW_VERIFICATION_TOKEN to "",
                    KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED to "false",
                    KryptxDbSchema.KEY_HARDWARE_KEY_LABEL to "",
                    KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH to "",
                    KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE to ""
                )
                if (!dbHelper.setMetadataBatch(hwBatch)) {
                    return@withVaultKey KryptxResult.Error(
                        KryptxErrorType.DATABASE_ERROR,
                        "Failed to commit hardware security key removal metadata"
                    )
                }
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove hardware key: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to modify security keys")
    }

    override suspend fun unlockWithHardwareKey(masterPassword: CharArray, hardwareSecret: ByteArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        // Prefer the dedicated HW-key token (written by fixed enrollment code).
        // Fall back to the legacy combined KEY_SALT / KEY_VERIFICATION_TOKEN for vaults
        // enrolled before this fix, ensuring backward compatibility.
        val hwSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_HW_SALT)
        val hwTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_HW_VERIFICATION_TOKEN)
        val useDedicatedHwKeys = !hwSaltBase64.isNullOrBlank() && !hwTokenBase64.isNullOrBlank()

        val saltBase64 = if (useDedicatedHwKeys) hwSaltBase64 else dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val tokenBase64 = if (useDedicatedHwKeys) hwTokenBase64 else dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)
        val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)

        if (saltBase64 != null && tokenBase64 != null) {
            val baseSalt = base64Decode(saltBase64)
            val tokenBytes = base64Decode(tokenBase64)

            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(baseSalt)
            md.update(hardwareSecret)
            val combinedSalt = md.digest()

            // Extend combinedSalt to 32 bytes for Argon2id minimum salt requirement
            val argon2Salt = combinedSalt.copyOf(32)
            // Use the stored KDF algorithm; new enrollments use Argon2id, legacy may use PBKDF2
            val derivedMasterKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(masterPassword, argon2Salt)
            } else {
                KeyDerivation.deriveKey(masterPassword, combinedSalt)
            }

            var vek: ByteArray? = null
            try {
                vek = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)

                val dbKey = deriveSqlCipherKey(vek)
                try {
                    dbHelper.setDatabaseKey(dbKey)
                    decoyDbHelper.setDatabaseKey(dbKey)
                } finally {
                    SecureMemory.wipe(dbKey)
                }

                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.withVaultKey { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                onAuditInvalidated?.invoke()
                return@withContext KryptxResult.Success(Unit)
            } catch (_: Exception) {
                // Handled below with failed attempt recording
            } finally {
                vek?.let { SecureMemory.wipe(it) }
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
                SecureMemory.wipe(argon2Salt)
            }
        }

        sessionManager.recordFailedAttempt()
        KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Hardware key validation or master password failed")
    }

    override fun getActiveVaultId(): String {
        return dbHelper.getMetadata(KryptxDbSchema.KEY_ACTIVE_VAULT) ?: "personal"
    }

    override suspend fun switchVault(vaultId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val saved = dbHelper.setMetadata(KryptxDbSchema.KEY_ACTIVE_VAULT, vaultId)
        if (!saved) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.DATABASE_ERROR,
                "Failed to persist active vault selection"
            )
        }
        sessionManager.withVaultKey { key ->
            dbHelper.loadAllItems(key)
        }
        onAuditInvalidated?.invoke()
        KryptxResult.Success(Unit)
    }

    override suspend fun resetVault(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.lock()
        val failures = mutableListOf<String>()

        try {
            if (!keystoreManager.removeBiometricKey()) {
                failures.add("Biometric key removal incomplete (key still present or Keystore error)")
            }
        } catch (e: Exception) {
            failures.add("Biometric key removal error: ${e.message}")
        }

        try {
            if (!dbHelper.clearAllData()) {
                failures.add("Primary database/storage cleanup incomplete")
            }
        } catch (e: Exception) {
            failures.add("Primary database cleanup error: ${e.message}")
        }

        try {
            if (!decoyDbHelper.clearAllData()) {
                failures.add("Decoy database/storage cleanup incomplete")
            }
        } catch (e: Exception) {
            failures.add("Decoy database cleanup error: ${e.message}")
        }

        if (failures.isNotEmpty()) {
            SecurityLogger.warn("VaultAuthRepository", "Reset vault incomplete: ${failures.joinToString("; ")}")
            KryptxResult.Error(
                KryptxErrorType.UNKNOWN,
                "Vault reset completed with errors: ${failures.joinToString("; ")}"
            )
        } else {
            KryptxResult.Success(Unit)
        }
    }

    override fun getPqcIdentityPublicKey(): ByteArray? {
        val base64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY) ?: return null
        return try {
            base64Decode(base64)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun rotatePqcIdentityKeyPair(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                generateAndStorePqcIdentityKeyPair(activeVek)
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to rotate PQC identity key pair", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    private fun generateAndStorePqcIdentityKeyPair(vek: ByteArray) {
        val pqcKeyPair = PostQuantumEngine.generateKeyPair()
        var encryptedPrivKey: ByteArray? = null
        try {
            encryptedPrivKey = CryptoEngine.encrypt(pqcKeyPair.privateKey, vek)
            val pqcBatch = mapOf(
                KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY to base64Encode(pqcKeyPair.publicKey),
                KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT to base64Encode(encryptedPrivKey)
            )
            if (!dbHelper.setMetadataBatch(pqcBatch)) {
                throw IllegalStateException("Failed to commit PQC identity key pair metadata batch")
            }
        } finally {
            SecureMemory.wipe(pqcKeyPair.privateKey)
            encryptedPrivKey?.let { SecureMemory.wipe(it) }
        }
    }

    private fun deriveSqlCipherKey(vek: ByteArray): ByteArray {
        val hkdf = org.bouncycastle.crypto.generators.HKDFBytesGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        val info = "Kryptx-SQLCipher-PageKey-v1".toByteArray(Charsets.UTF_8)
        // RFC 5869 §2.2: A non-null salt provides domain separation and additional defense-in-depth.
        // Using a static application-level salt since the VEK itself already carries sufficient entropy.
        val salt = "Kryptx-SQLCipher-Salt-v1".toByteArray(Charsets.UTF_8)
        hkdf.init(org.bouncycastle.crypto.params.HKDFParameters(vek, salt, info))
        val out = ByteArray(32)
        hkdf.generateBytes(out, 0, 32)
        return out
    }

    private fun deriveMasterKeyForVault(password: CharArray, salt: ByteArray): ByteArray {
        val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)
        return if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
            val memoryKb = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_MEMORY_KB)?.toIntOrNull()
            val iterations = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_ITERATIONS)?.toIntOrNull()
            val parallelism = dbHelper.getMetadata(KryptxDbSchema.KEY_ARGON2_PARALLELISM)?.toIntOrNull()
            val params = if (memoryKb != null && iterations != null && parallelism != null) {
                Argon2Engine.Argon2Params(
                    memoryCostKb = memoryKb,
                    iterations = iterations,
                    parallelism = parallelism,
                    outputLength = 32
                )
            } else {
                Argon2Engine.Argon2Params.forDevice()
            }
            KeyDerivation.deriveKeyArgon2(password, salt, params)
        } else {
            KeyDerivation.deriveKey(password, salt)
        }
    }
}
