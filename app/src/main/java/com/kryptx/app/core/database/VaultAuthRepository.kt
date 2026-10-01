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
        return keystoreManager.hasBiometricKey() &&
                !dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK).isNullOrBlank()
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
            val panicHashBase64 = dbHelper.getMetadata("panic_hash")
            if (panicSaltBase64 != null && panicHashBase64 != null) {
                val panicSalt = Base64.decode(panicSaltBase64, Base64.NO_WRAP)
                val expectedHash = Base64.decode(panicHashBase64, Base64.NO_WRAP)
                val derivedPanicKey = KeyDerivation.deriveKey(masterPassword, panicSalt)
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val actualHash = md.digest(derivedPanicKey)

                if (actualHash.contentEquals(expectedHash)) {
                    SecureMemory.wipe(derivedPanicKey)
                    SecureMemory.wipe(panicSalt)
                    triggerPanicSelfDestruct()
                    return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault has been permanently wiped.")
                }
                SecureMemory.wipe(derivedPanicKey)
                SecureMemory.wipe(panicSalt)
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
                decoyDbHelper.setDatabaseKey(dbKey)
                SecureMemory.wipe(dbKey)

                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.withVaultKey { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                success = true
                onAuditInvalidated?.invoke()
            } catch (_: Exception) {
                // Decryption failure falls through
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
                } catch (_: Exception) {
                    // Decryption failure falls through
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

    override suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit> = setupBiometrics()

    override suspend fun unlockWithBiometrics(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val cipher = getBiometricDecryptCipher()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.KEYSTORE_INVALIDATED, "Biometric key invalidated — please re-enroll")
        unlockWithBiometricCipher(cipher)
    }

    override suspend fun unlockWithBiometricCipher(cipher: Cipher): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val wrappedBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.BIOMETRICS_NOT_AVAILABLE, "No biometric key stored")
        try {
            val wrappedBytes = Base64.decode(wrappedBase64, Base64.NO_WRAP)
            val vek = keystoreManager.unwrapWithCipher(cipher, wrappedBytes)

            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            decoyDbHelper.setDatabaseKey(dbKey)
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

            val newVek = KeyDerivation.generateSalt(32)

            try {
                dbHelper.reEncryptVaultWithNewKey(activeVek, newVek)

                // Rekey SQLCipher database file encryption to match new VEK
                val newDbKey = deriveSqlCipherKey(newVek)
                dbHelper.rekeyDatabase(newDbKey)
                SecureMemory.wipe(newDbKey)

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
                SecureMemory.wipe(newVek)
                SecureMemory.wipe(currentDerivedKey)
                SecureMemory.wipe(salt)
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to rotate encryption key: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun hasDuressPassword(): Boolean = dbHelper.hasDuressSetup()

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
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
        try {
            val salt = KeyDerivation.generateSalt()
            val derivedKey = KeyDerivation.deriveKey(panicPassword, salt)
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest(derivedKey)

            dbHelper.setMetadata("panic_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            dbHelper.setMetadata("panic_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            dbHelper.setMetadata("has_panic_setup", "true")

            SecureMemory.wipe(derivedKey)
            SecureMemory.wipe(salt)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to setup panic password", e)
        }
    }

    override suspend fun removePanicPassword() = withContext(Dispatchers.IO) {
        dbHelper.setMetadata("panic_salt", "")
        dbHelper.setMetadata("panic_hash", "")
        dbHelper.setMetadata("has_panic_setup", "false")
    }

    override suspend fun triggerPanicSelfDestruct() = withContext(Dispatchers.IO) {
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

                val derivedKey = KeyDerivation.deriveKey(masterPassword, combinedSalt)
                val encryptedVekPayload = CryptoEngine.encrypt(activeVek, derivedKey)

                val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
                val saltBase64 = Base64.encodeToString(baseSalt, Base64.NO_WRAP)
                val challengeBase64 = Base64.encodeToString(challenge, Base64.NO_WRAP)

                dbHelper.setMetadata(KryptxDbSchema.KEY_SALT, saltBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, tokenBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED, "true")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL, label)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH, uidHash)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE, challengeBase64)

                SecureMemory.wipe(derivedKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)

                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to enroll hardware security key", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to enroll hardware key")
    }

    override suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val newSalt = KeyDerivation.generateSalt()
                val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, newSalt)
                val encryptedVekPayload = CryptoEngine.encrypt(activeVek, derivedMasterKey)

                val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
                val saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)

                dbHelper.setMetadata(KryptxDbSchema.KEY_SALT, saltBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN, tokenBase64)
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED, "false")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_LABEL, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH, "")
                dbHelper.setMetadata(KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE, "")

                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(newSalt)

                KryptxResult.Success(Unit)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove hardware key", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to modify security keys")
    }

    override suspend fun unlockWithHardwareKey(masterPassword: CharArray, hardwareSecret: ByteArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val saltBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN)

        if (saltBase64 != null && tokenBase64 != null) {
            val baseSalt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)

            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(baseSalt)
            md.update(hardwareSecret)
            val combinedSalt = md.digest()

            val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, combinedSalt)

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
                onAuditInvalidated?.invoke()
                return@withContext KryptxResult.Success(Unit)
            } catch (_: Exception) {
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
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
        hkdf.init(org.bouncycastle.crypto.params.HKDFParameters(vek, null, info))
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
