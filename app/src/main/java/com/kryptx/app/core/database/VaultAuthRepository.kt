package com.kryptx.app.core.database

import android.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.core.security.SecurityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

interface VaultAuthRepository {
    fun hasVault(): Boolean
    fun isBiometricsConfigured(): Boolean
    fun getBiometricDecryptCipher(): Cipher?
    fun getBiometricEncryptCipher(): Cipher?

    suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun setupBiometrics(): KryptxResult<Unit>
    suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit>
    suspend fun unlockWithBiometrics(): KryptxResult<Unit>
    suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit>
    suspend fun disableBiometrics()
    suspend fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray): KryptxResult<Unit>
    suspend fun rotateVaultEncryptionKey(currentMasterPassword: CharArray): KryptxResult<Unit>

    fun hasDuressPassword(): Boolean
    suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit>
    suspend fun removeDuressPassword()

    fun hasPanicPassword(): Boolean
    suspend fun setupPanicPassword(panicPassword: CharArray): KryptxResult<Unit>
    suspend fun removePanicPassword()
    suspend fun triggerPanicSelfDestruct()

    fun isHardwareKeyEnrolled(): Boolean
    fun getHardwareKeyLabel(): String?
    fun getHardwareKeyChallenge(): ByteArray?
    fun getHardwareKeyUidHash(): String?
    suspend fun enrollHardwareKey(label: String, uidHash: String, challenge: ByteArray, hardwareSecret: ByteArray, masterPassword: CharArray): KryptxResult<Unit>
    suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit>
    suspend fun unlockWithHardwareKey(masterPassword: CharArray, hardwareSecret: ByteArray): KryptxResult<Unit>

    fun getActiveVaultId(): String
    suspend fun switchVault(vaultId: String): KryptxResult<Unit>
    suspend fun resetVault()

    fun getPqcIdentityPublicKey(): ByteArray?
    suspend fun rotatePqcIdentityKeyPair(): KryptxResult<Unit>
}

class VaultAuthRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val decoyDbHelper: KryptxDatabaseHelper,
    private val sessionManager: VaultSessionManager,
    private val keystoreManager: KeystoreManager,
    private val onAuditInvalidated: (() -> Unit)? = null
) : VaultAuthRepository {

    override fun hasVault(): Boolean = dbHelper.hasVaultSetup()

    override fun isBiometricsConfigured(): Boolean {
        val hasWrappedKey = !dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK).isNullOrBlank()
        return hasWrappedKey && keystoreManager.hasBiometricKey() && !keystoreManager.isBiometricKeyPermanentlyInvalidated()
    }

    override fun getBiometricDecryptCipher(): Cipher? {
        return try {
            keystoreManager.getDecryptCipher()
        } catch (_: Exception) {
            null
        }
    }

    override fun getBiometricEncryptCipher(): Cipher? = keystoreManager.getEncryptCipher()

    override suspend fun setupNewVault(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val salt = KeyDerivation.generateSalt()
        var derivedMasterKey: ByteArray? = null
        var vek: ByteArray? = null
        try {
            derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt)
            vek = CryptoEngine.generateVaultKey()

            val encryptedVekPayload = CryptoEngine.encrypt(vek, derivedMasterKey)
            val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            decoyDbHelper.setDatabaseKey(dbKey)
            SecureMemory.wipe(dbKey)

            dbHelper.setMetadata(KryptxDbSchema.KEY_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM, KeyDerivation.KdfAlgorithm.ARGON2ID.identifier)
            dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDbSchema.KEY_HAS_SETUP, "true")

            generateAndStorePqcIdentityKeyPair(vek)

            dbHelper.recordSecurityScore(100)
            sessionManager.unlock(vek)

            onAuditInvalidated?.invoke()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to create vault", e)
        } finally {
            SecureMemory.wipe(vek)
            SecureMemory.wipe(derivedMasterKey)
            SecureMemory.wipe(salt)
        }
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        if (hasPanicPassword()) {
            val panicSaltBase64 = dbHelper.getMetadata("panic_salt")
            val panicTokenBase64 = dbHelper.getMetadata("panic_token")
            val panicHashBase64 = dbHelper.getMetadata("panic_hash")
            val panicKdfAlgorithm = dbHelper.getMetadata("panic_kdf_algorithm")
            if (panicSaltBase64 != null && (!panicTokenBase64.isNullOrBlank() || !panicHashBase64.isNullOrBlank())) {
                val panicSalt = Base64.decode(panicSaltBase64, Base64.NO_WRAP)
                // Use Argon2id for new entries; fall back to PBKDF2 for legacy entries
                val derivedPanicKey = if (panicKdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                    KeyDerivation.deriveKeyArgon2(masterPassword, panicSalt)
                } else {
                    KeyDerivation.deriveKey(masterPassword, panicSalt)
                }
                var isPanicMatch = false
                if (!panicTokenBase64.isNullOrBlank()) {
                    try {
                        val tokenBytes = Base64.decode(panicTokenBase64, Base64.NO_WRAP)
                        val decrypted = CryptoEngine.decrypt(tokenBytes, derivedPanicKey, "kryptx-panic-auth".toByteArray(Charsets.UTF_8))
                        SecureMemory.wipe(decrypted)
                        isPanicMatch = true
                    } catch (_: Exception) {
                        // Expected if password doesn't match panic PIN
                    }
                } else if (!panicHashBase64.isNullOrBlank()) {
                    // Legacy fallback for SHA-256 hash stored passwords
                    val expectedHash = Base64.decode(panicHashBase64, Base64.NO_WRAP)
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val actualHash = md.digest(derivedPanicKey)
                    if (SecureMemory.safeEquals(actualHash, expectedHash)) {
                        isPanicMatch = true
                    }
                }

                SecureMemory.wipe(derivedPanicKey)
                SecureMemory.wipe(panicSalt)

                if (isPanicMatch) {
                    triggerPanicSelfDestruct()
                    return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault has been permanently wiped.")
                }
            }
        }

        val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)

        if (saltBase64 != null && tokenBase64 != null) {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)

            val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)
            val derivedMasterKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(masterPassword, salt)
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
                val duressSalt = Base64.decode(duressSaltBase64, Base64.NO_WRAP)
                val duressTokenBytes = Base64.decode(duressTokenBase64, Base64.NO_WRAP)
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
                val wrappedVek = keystoreManager.wrapWithPublicKey(activeVek)
                val wrappedBase64 = Base64.encodeToString(wrappedVek, Base64.NO_WRAP)

                dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK, wrappedBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_IV, "rsa_oaep")
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
                val wrappedBase64 = Base64.encodeToString(wrappedVek, Base64.NO_WRAP)

                dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK, wrappedBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_IV, "rsa_oaep")
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Failed to enroll biometric key with cipher", e)
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
        try {
            val wrappedBytes = Base64.decode(wrappedBase64, Base64.NO_WRAP)
            val vek = keystoreManager.unwrapWithCipher(cipher, wrappedBytes)

            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            SecureMemory.wipe(dbKey)

            sessionManager.unlock(vek)
            sessionManager.withVaultKey { activeKey ->
                dbHelper.loadAllItems(activeKey)
            }
            SecureMemory.wipe(vek)
            onAuditInvalidated?.invoke()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            sessionManager.recordFailedAttempt()
            KryptxResult.Error(KryptxErrorType.BIOMETRICS_FAILED, "Biometric decryption failed", e)
        }
    }

    override suspend fun disableBiometrics() = withContext(Dispatchers.IO) {
        keystoreManager.removeBiometricKey()
        dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK, "")
        dbHelper.setMetadata(KryptxDbSchema.KEY_BIOMETRIC_IV, "")
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

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)
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

            val newSalt = KeyDerivation.generateSalt()
            val newDerivedKey = KeyDerivation.deriveKeyArgon2(newPassword, newSalt)
            val newEncryptedVek = CryptoEngine.encrypt(activeVek, newDerivedKey)

            dbHelper.setMetadata(KryptxDbSchema.KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
            dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, Base64.encodeToString(newEncryptedVek, Base64.NO_WRAP))
            dbHelper.setMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM, KeyDerivation.KdfAlgorithm.ARGON2ID.identifier)

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

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)
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
            var reEncrypted = false
            var rekeyed = false

            try {
                // Step 1: Re-encrypt all vault items under new VEK
                dbHelper.reEncryptVaultWithNewKey(activeVek, newVek)
                reEncrypted = true

                // Step 2: Rekey SQLCipher database file encryption to match new VEK
                val newDbKey = deriveSqlCipherKey(newVek)
                dbHelper.rekeyDatabase(newDbKey)
                SecureMemory.wipe(newDbKey)
                rekeyed = true

                // Step 3: Encrypt new VEK under master password and update verification token
                val newEncryptedVek = CryptoEngine.encrypt(newVek, currentDerivedKey)
                dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, Base64.encodeToString(newEncryptedVek, Base64.NO_WRAP))

                sessionManager.unlock(newVek, isDecoy = false)

                if (isBiometricsConfigured()) {
                    setupBiometrics()
                }

                SecureMemory.wipe(newVek)
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
                onAuditInvalidated?.invoke()
                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                // Rollback partially applied rotation steps to preserve complete vault consistency
                if (rekeyed) {
                    val oldDbKey = deriveSqlCipherKey(activeVek)
                    try {
                        dbHelper.rekeyDatabase(oldDbKey)
                    } catch (rollbackRekeyEx: Exception) {
                        SecurityLogger.error("VaultAuthRepository", "Rollback rekeyDatabase failed", rollbackRekeyEx)
                    } finally {
                        SecureMemory.wipe(oldDbKey)
                    }
                }
                if (reEncrypted) {
                    try {
                        dbHelper.reEncryptVaultWithNewKey(newVek, activeVek)
                    } catch (rollbackReEncryptEx: Exception) {
                        SecurityLogger.error("VaultAuthRepository", "Rollback reEncryptVaultWithNewKey failed", rollbackReEncryptEx)
                    }
                }

                SecureMemory.wipe(newVek)
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
                // Log internally but do NOT expose raw exception details to user-facing messages
                SecurityLogger.error("VaultAuthRepository", "Encryption key rotation failed (rolled back)", e)
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to rotate encryption key. Please try again.", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun hasDuressPassword(): Boolean = dbHelper.hasDuressSetup()

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val masterSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val masterTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)
        if (masterSaltBase64 != null && masterTokenBase64 != null) {
            val masterSalt = Base64.decode(masterSaltBase64, Base64.NO_WRAP)
            val masterToken = Base64.decode(masterTokenBase64, Base64.NO_WRAP)
            val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)
            val testDerivedKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(duressPassword, masterSalt)
            } else {
                KeyDerivation.deriveKey(duressPassword, masterSalt)
            }
            var matchesMaster = false
            try {
                val decrypted = CryptoEngine.decrypt(masterToken, testDerivedKey)
                SecureMemory.wipe(decrypted)
                matchesMaster = true
            } catch (_: Exception) {
            } finally {
                SecureMemory.wipe(testDerivedKey)
                SecureMemory.wipe(masterSalt)
            }
            if (matchesMaster) {
                return@withContext KryptxResult.Error(
                    KryptxErrorType.WRONG_PASSWORD,
                    "Duress PIN cannot be identical to your master password"
                )
            }
        }

        if (hasPanicPassword()) {
            val panicSaltBase64 = dbHelper.getMetadata("panic_salt")
            val panicHashBase64 = dbHelper.getMetadata("panic_hash")
            val panicKdfAlgorithm = dbHelper.getMetadata("panic_kdf_algorithm")
            if (panicSaltBase64 != null && panicHashBase64 != null) {
                val pSalt = Base64.decode(panicSaltBase64, Base64.NO_WRAP)
                val expectedHash = Base64.decode(panicHashBase64, Base64.NO_WRAP)
                val testPanicKey = if (panicKdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                    KeyDerivation.deriveKeyArgon2(duressPassword, pSalt)
                } else {
                    KeyDerivation.deriveKey(duressPassword, pSalt)
                }
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val actualHash = md.digest(testPanicKey)
                val matchesPanic = SecureMemory.safeEquals(actualHash, expectedHash)
                SecureMemory.wipe(testPanicKey)
                SecureMemory.wipe(pSalt)
                if (matchesPanic) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.WRONG_PASSWORD,
                        "Duress PIN cannot be identical to your Panic PIN"
                    )
                }
            }
        }

        try {
            val salt = KeyDerivation.generateSalt()
            val derivedDuressKey = KeyDerivation.deriveKeyArgon2(duressPassword, salt)
            val decoyVek = CryptoEngine.generateVaultKey()
            val encryptedDecoyPayload = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

            val tokenBase64 = Base64.encodeToString(encryptedDecoyPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDbSchema.KEY_DURESS_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDbSchema.KEY_DURESS_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDbSchema.KEY_HAS_DURESS, "true")

            val decoyDbKey = deriveSqlCipherKey(decoyVek)
            decoyDbHelper.setDatabaseKey(decoyDbKey)
            SecureMemory.wipe(decoyDbKey)

            decoyDbHelper.provisionDefaultItems(decoyVek)

            SecureMemory.wipe(decoyVek)
            SecureMemory.wipe(derivedDuressKey)
            SecureMemory.wipe(salt)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup duress vault", e)
        }
    }

    override suspend fun removeDuressPassword() = withContext(Dispatchers.IO) {
        dbHelper.setMetadata(KryptxDbSchema.KEY_DURESS_SALT, "")
        dbHelper.setMetadata(KryptxDbSchema.KEY_DURESS_TOKEN, "")
        dbHelper.setMetadata(KryptxDbSchema.KEY_HAS_DURESS, "false")
    }

    override fun hasPanicPassword(): Boolean = dbHelper.getMetadata("has_panic_setup") == "true"

    override suspend fun setupPanicPassword(panicPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val masterSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val masterTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)
        if (masterSaltBase64 != null && masterTokenBase64 != null) {
            val masterSalt = Base64.decode(masterSaltBase64, Base64.NO_WRAP)
            val masterToken = Base64.decode(masterTokenBase64, Base64.NO_WRAP)
            val kdfAlgorithm = dbHelper.getMetadata(KryptxDbSchema.KEY_KDF_ALGORITHM)
            val testDerivedKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(panicPassword, masterSalt)
            } else {
                KeyDerivation.deriveKey(panicPassword, masterSalt)
            }
            var matchesMaster = false
            try {
                val decrypted = CryptoEngine.decrypt(masterToken, testDerivedKey)
                SecureMemory.wipe(decrypted)
                matchesMaster = true
            } catch (_: Exception) {
            } finally {
                SecureMemory.wipe(testDerivedKey)
                SecureMemory.wipe(masterSalt)
            }
            if (matchesMaster) {
                return@withContext KryptxResult.Error(
                    KryptxErrorType.WRONG_PASSWORD,
                    "Panic PIN cannot be identical to your master password"
                )
            }
        }

        if (hasDuressPassword()) {
            val duressSaltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_SALT)
            val duressTokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_TOKEN)
            if (duressSaltBase64 != null && duressTokenBase64 != null) {
                val dSalt = Base64.decode(duressSaltBase64, Base64.NO_WRAP)
                val dToken = Base64.decode(duressTokenBase64, Base64.NO_WRAP)
                val testDuressKey = KeyDerivation.deriveKeyArgon2(panicPassword, dSalt)
                var matchesDuress = false
                try {
                    val decrypted = CryptoEngine.decrypt(dToken, testDuressKey)
                    SecureMemory.wipe(decrypted)
                    matchesDuress = true
                } catch (_: Exception) {
                } finally {
                    SecureMemory.wipe(testDuressKey)
                    SecureMemory.wipe(dSalt)
                }
                if (matchesDuress) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.WRONG_PASSWORD,
                        "Panic PIN cannot be identical to your Duress PIN"
                    )
                }
            }
        }

        try {
            val salt = KeyDerivation.generateSalt()
            // Use Argon2id consistently with the master password KDF for uniform security
            val derivedKey = KeyDerivation.deriveKeyArgon2(panicPassword, salt)
            val panicVerificationToken = CryptoEngine.generateVaultKey()
            val encryptedToken = try {
                CryptoEngine.encrypt(panicVerificationToken, derivedKey, "kryptx-panic-auth".toByteArray(Charsets.UTF_8))
            } finally {
                SecureMemory.wipe(panicVerificationToken)
            }

            dbHelper.setMetadata("panic_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            dbHelper.setMetadata("panic_token", Base64.encodeToString(encryptedToken, Base64.NO_WRAP))
            // Clear legacy hash
            dbHelper.setMetadata("panic_hash", "")
            dbHelper.setMetadata("has_panic_setup", "true")
            // Store KDF algorithm used so future versions can maintain backward compatibility
            dbHelper.setMetadata("panic_kdf_algorithm", KeyDerivation.KdfAlgorithm.ARGON2ID.identifier)

            SecureMemory.wipe(derivedKey)
            SecureMemory.wipe(salt)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup panic password", e)
        }
    }

    override suspend fun removePanicPassword() = withContext(Dispatchers.IO) {
        dbHelper.setMetadata("panic_salt", "")
        dbHelper.setMetadata("panic_token", "")
        dbHelper.setMetadata("panic_hash", "")
        dbHelper.setMetadata("has_panic_setup", "false")
    }

    override suspend fun triggerPanicSelfDestruct() = withContext(Dispatchers.Default) {
        // Deliberately uses Dispatchers.Default (not IO) to be consistent with all other crypto-touching
        // operations. The file deletions here are fast destructive writes, not large-file IO.
        dbHelper.clearAllData()
        decoyDbHelper.clearAllData()
        keystoreManager.removeBiometricKey()
        sessionManager.lockVault()
    }

    override fun isHardwareKeyEnrolled(): Boolean {
        return dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED) == "true" &&
                !dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE).isNullOrBlank()
    }

    override fun getHardwareKeyLabel(): String? = dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL)

    override fun getHardwareKeyChallenge(): ByteArray? {
        val challengeBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE) ?: return null
        return try {
            Base64.decode(challengeBase64, Base64.NO_WRAP)
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
            try {
                val baseSalt = KeyDerivation.generateSalt()
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.update(baseSalt)
                md.update(hardwareSecret)
                val combinedSalt = md.digest()

                // Extend combinedSalt to 32 bytes for Argon2id minimum salt requirement
                val argon2Salt = combinedSalt.copyOf(32)
                // Use Argon2id consistently with master password KDF — hardware key should
                // have equal or greater brute-force resistance than the primary unlock path
                val derivedKey = KeyDerivation.deriveKeyArgon2(masterPassword, argon2Salt)
                val encryptedVekPayload = CryptoEngine.encrypt(activeVek, derivedKey)

                val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
                val saltBase64 = Base64.encodeToString(baseSalt, Base64.NO_WRAP)
                val challengeBase64 = Base64.encodeToString(challenge, Base64.NO_WRAP)

                // Write to HW-specific keys — never overwrite the master password's KEY_SALT /
                // KEY_VERIFICATION_TOKEN so the password-only unlock path remains intact as a
                // permanent recovery option even after hardware key enrollment.
                dbHelper.setMetadata(KryptxDbSchema.KEY_HW_SALT, saltBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HW_VERIFICATION_TOKEN, tokenBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED, "true")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL, label)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH, uidHash)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE, challengeBase64)

                SecureMemory.wipe(derivedKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
                SecureMemory.wipe(argon2Salt)

                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to enroll hardware security key", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to enroll hardware key")
    }

    override suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { _ ->
            try {
                // Clear the hardware-key-specific token — the master password token
                // (KEY_SALT / KEY_VERIFICATION_TOKEN) was never overwritten during enrollment
                // and remains valid for password-only unlock.
                dbHelper.setMetadata(KryptxDbSchema.KEY_HW_SALT, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HW_VERIFICATION_TOKEN, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED, "false")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE, "")

                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove hardware key", e)
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
            val baseSalt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)

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

            try {
                val vek = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)

                val dbKey = deriveSqlCipherKey(vek)
                dbHelper.setDatabaseKey(dbKey)
                decoyDbHelper.setDatabaseKey(dbKey)
                SecureMemory.wipe(dbKey)

                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.withVaultKey { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                SecureMemory.wipe(vek)
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
                SecureMemory.wipe(argon2Salt)
                onAuditInvalidated?.invoke()
                return@withContext KryptxResult.Success(Unit)
            } catch (_: Exception) {
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
        dbHelper.setMetadata(KryptxDbSchema.KEY_ACTIVE_VAULT, vaultId)
        sessionManager.withVaultKey { key ->
            dbHelper.loadAllItems(key)
        }
        onAuditInvalidated?.invoke()
        KryptxResult.Success(Unit)
    }

    override suspend fun resetVault() = withContext(Dispatchers.IO) {
        sessionManager.lock()
        keystoreManager.removeBiometricKey()
        dbHelper.clearAllData()
        decoyDbHelper.clearAllData()
    }

    override fun getPqcIdentityPublicKey(): ByteArray? {
        val base64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY) ?: return null
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
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
        val encryptedPrivKey = CryptoEngine.encrypt(pqcKeyPair.privateKey, vek)
        dbHelper.setMetadata(
            KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY,
            Base64.encodeToString(pqcKeyPair.publicKey, Base64.NO_WRAP)
        )
        dbHelper.setMetadata(
            KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT,
            Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP)
        )
        SecureMemory.wipe(pqcKeyPair.privateKey)
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
            KeyDerivation.deriveKeyArgon2(password, salt)
        } else {
            KeyDerivation.deriveKey(password, salt)
        }
    }
}
