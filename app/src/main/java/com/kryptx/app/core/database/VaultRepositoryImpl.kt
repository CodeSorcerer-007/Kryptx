package com.kryptx.app.core.database

import android.util.Base64
import com.kryptx.app.core.crypto.NativeCryptoEngineWrapper as CryptoEngine
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.PasskeyEngine
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.migration.VaultExporter
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

@OptIn(ExperimentalCoroutinesApi::class)
class VaultRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val decoyDbHelper: KryptxDatabaseHelper,
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
            // Use Argon2id as the default KDF — memory-hard and side-channel resistant.
            val derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt)
            val vek = CryptoEngine.generateVaultKey()

            val encryptedVekPayload = CryptoEngine.encrypt(vek, derivedMasterKey)
            val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            // Derive a separate SQLCipher page-encryption key from the VEK using HKDF.
            // This ensures the DB file is also page-encrypted, providing defence-in-depth
            // alongside the existing field-level AES-256-GCM blobs.
            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            decoyDbHelper.setDatabaseKey(dbKey) // decoy uses same session key
            SecureMemory.wipe(dbKey)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_KDF_ALGORITHM, KeyDerivation.KdfAlgorithm.ARGON2ID.identifier)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_SETUP, "true")

            // Generate a persistent ML-KEM-768 identity key pair for this vault.
            // The public key is stored in plaintext metadata (for backup recipient encapsulation).
            // The private key is AES-256-GCM encrypted under the VEK and stored in metadata.
            generateAndStorePqcIdentityKeyPair(vek)

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

    /**
     * Generates a fresh ML-KEM-768 identity key pair and persists it:
     * - Public key → plaintext in EncryptedSharedPreferences (safe to share for encapsulation)
     * - Private key → AES-256-GCM encrypted under the VEK, stored in EncryptedSharedPreferences
     */
    private fun generateAndStorePqcIdentityKeyPair(vek: ByteArray) {
        val pqcKeyPair = PostQuantumEngine.generateKeyPair()
        val encryptedPrivKey = CryptoEngine.encrypt(pqcKeyPair.privateKey, vek)
        dbHelper.setMetadata(
            KryptxDatabaseHelper.KEY_PQC_IDENTITY_PUBLIC_KEY,
            Base64.encodeToString(pqcKeyPair.publicKey, Base64.NO_WRAP)
        )
        dbHelper.setMetadata(
            KryptxDatabaseHelper.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT,
            Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP)
        )
        SecureMemory.wipe(pqcKeyPair.privateKey)
    }

    /**
     * Derives a 32-byte SQLCipher page-encryption key from the active VEK using HKDF-SHA256.
     * Using a separate derived key means the SQLCipher key rotates whenever the VEK rotates,
     * and never equals the VEK itself.
     */
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

    override fun hasDuressPassword(): Boolean = dbHelper.hasDuressSetup()

    override suspend fun setupDuressPassword(duressPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        try {
            val salt = KeyDerivation.generateSalt()
            val derivedDuressKey = KeyDerivation.deriveKeyArgon2(duressPassword, salt)
            val decoyVek = CryptoEngine.generateVaultKey()
            val encryptedDecoyPayload = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

            val tokenBase64 = Base64.encodeToString(encryptedDecoyPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_DURESS, "true")

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
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_SALT, "")
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_DURESS_TOKEN, "")
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HAS_DURESS, "false")
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
        dbHelper.clearAllData()      // also clears the db key
        decoyDbHelper.clearAllData()
        keystoreManager.removeBiometricKey()
        sessionManager.lockVault()
    }

    override fun isHardwareKeyEnrolled(): Boolean {
        return dbHelper.getMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_ENROLLED) == "true" &&
                !dbHelper.getMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_CHALLENGE).isNullOrBlank()
    }

    override fun getHardwareKeyLabel(): String? = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_LABEL)

    override fun getHardwareKeyChallenge(): ByteArray? {
        val challengeBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_CHALLENGE) ?: return null
        return try {
            Base64.decode(challengeBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override fun getHardwareKeyUidHash(): String? = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_UID_HASH)

    override suspend fun enrollHardwareKey(
        label: String,
        uidHash: String,
        challenge: ByteArray,
        hardwareSecret: ByteArray,
        masterPassword: CharArray
    ): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        try {
            val activeVek = sessionManager.getVaultKey()
                ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to enroll hardware key")

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

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_ENROLLED, "true")
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_LABEL, label)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_UID_HASH, uidHash)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_CHALLENGE, challengeBase64)

            SecureMemory.wipe(derivedKey)
            SecureMemory.wipe(baseSalt)
            SecureMemory.wipe(combinedSalt)

            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to enroll hardware security key", e)
        }
    }

    override suspend fun removeHardwareKey(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        try {
            val activeVek = sessionManager.getVaultKey()
                ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault must be unlocked to modify security keys")

            val newSalt = KeyDerivation.generateSalt()
            val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, newSalt)
            val encryptedVekPayload = CryptoEngine.encrypt(activeVek, derivedMasterKey)

            val tokenBase64 = Base64.encodeToString(encryptedVekPayload, Base64.NO_WRAP)
            val saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)

            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, saltBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, tokenBase64)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_ENROLLED, "false")
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_LABEL, "")
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_UID_HASH, "")
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_HARDWARE_KEY_CHALLENGE, "")

            SecureMemory.wipe(derivedMasterKey)
            SecureMemory.wipe(newSalt)

            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to remove hardware key", e)
        }
    }

    override suspend fun unlockWithHardwareKey(masterPassword: CharArray, hardwareSecret: ByteArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val saltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN)

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

                // Wire SQLCipher key before opening the database
                val dbKey = deriveSqlCipherKey(vek)
                dbHelper.setDatabaseKey(dbKey)
                decoyDbHelper.setDatabaseKey(dbKey)
                SecureMemory.wipe(dbKey)

                sessionManager.unlock(vek, isDecoy = false)
                sessionManager.getVaultKey()?.let { activeKey ->
                    dbHelper.loadAllItems(activeKey)
                }
                SecureMemory.wipe(vek)
                SecureMemory.wipe(derivedMasterKey)
                SecureMemory.wipe(baseSalt)
                SecureMemory.wipe(combinedSalt)
                isAuditDirty = true
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
        return dbHelper.getMetadata(KryptxDatabaseHelper.KEY_ACTIVE_VAULT) ?: "personal"
    }

    override suspend fun switchVault(vaultId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_ACTIVE_VAULT, vaultId)
        sessionManager.getVaultKey()?.let { key ->
            dbHelper.loadAllItems(key)
        }
        isAuditDirty = true
        KryptxResult.Success(Unit)
    }

    override suspend fun unlockWithPassword(masterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        // 0. Check Panic Password First
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

        // 1. Try Primary Master Password
        val saltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_SALT)
        val tokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN)

        if (saltBase64 != null && tokenBase64 != null) {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)

            // Respect the KDF algorithm that was used when the vault was created.
            val kdfAlgorithm = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_KDF_ALGORITHM)
            val derivedMasterKey = if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
                KeyDerivation.deriveKeyArgon2(masterPassword, salt)
            } else {
                KeyDerivation.deriveKey(masterPassword, salt)
            }

            try {
                val vek = CryptoEngine.decrypt(tokenBytes, derivedMasterKey)

                // Wire SQLCipher key before opening the database
                val dbKey = deriveSqlCipherKey(vek)
                dbHelper.setDatabaseKey(dbKey)
                decoyDbHelper.setDatabaseKey(dbKey)
                SecureMemory.wipe(dbKey)

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
                val derivedDuressKey = KeyDerivation.deriveKeyArgon2(masterPassword, duressSalt)

                try {
                    val decoyVek = CryptoEngine.decrypt(duressTokenBytes, derivedDuressKey)

                    val dbKey = deriveSqlCipherKey(decoyVek)
                    decoyDbHelper.setDatabaseKey(dbKey)
                    SecureMemory.wipe(dbKey)

                    sessionManager.unlock(decoyVek, isDecoy = true)
                    decoyDbHelper.loadAllItems(decoyVek)

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

            // Wire SQLCipher key before opening the database
            val dbKey = deriveSqlCipherKey(vek)
            dbHelper.setDatabaseKey(dbKey)
            decoyDbHelper.setDatabaseKey(dbKey)
            SecureMemory.wipe(dbKey)

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

    private fun deriveMasterKeyForVault(password: CharArray, salt: ByteArray): ByteArray {
        val kdfAlgorithm = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_KDF_ALGORITHM)
        return if (kdfAlgorithm == KeyDerivation.KdfAlgorithm.ARGON2ID.identifier) {
            KeyDerivation.deriveKeyArgon2(password, salt)
        } else {
            KeyDerivation.deriveKey(password, salt)
        }
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
        val currentDerivedKey = deriveMasterKeyForVault(currentPassword, salt)

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

        // Generate new salt and re-wrap VEK using memory-hard Argon2id
        val newSalt = KeyDerivation.generateSalt()
        val newDerivedKey = KeyDerivation.deriveKeyArgon2(newPassword, newSalt)
        val newEncryptedVek = CryptoEngine.encrypt(activeVek, newDerivedKey)

        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, Base64.encodeToString(newEncryptedVek, Base64.NO_WRAP))
        dbHelper.setMetadata(KryptxDatabaseHelper.KEY_KDF_ALGORITHM, KeyDerivation.KdfAlgorithm.ARGON2ID.identifier)

        if (isBiometricsConfigured()) {
            setupBiometrics()
        }

        SecureMemory.wipe(newDerivedKey)
        SecureMemory.wipe(newSalt)
        KryptxResult.Success(Unit)
    }

    override suspend fun rotateVaultEncryptionKey(currentMasterPassword: CharArray): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")

        val saltBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_SALT)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault salt not found")
        val tokenBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN)
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_NOT_FOUND, "Vault token not found")

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val tokenBytes = Base64.decode(tokenBase64, Base64.NO_WRAP)
        val currentDerivedKey = deriveMasterKeyForVault(currentMasterPassword, salt)

        val verifiedVek = try {
            CryptoEngine.decrypt(tokenBytes, currentDerivedKey)
        } catch (_: Exception) {
            SecureMemory.wipe(currentDerivedKey)
            SecureMemory.wipe(salt)
            return@withContext KryptxResult.Error(KryptxErrorType.WRONG_PASSWORD, "Incorrect master password")
        }
        SecureMemory.wipe(verifiedVek)

        // Generate brand new 256-bit VEK
        val newVek = KeyDerivation.generateSalt(32)

        try {
            // Re-encrypt all items on disk under atomic SQLite transaction
            dbHelper.reEncryptVaultWithNewKey(activeVek, newVek)

            // Re-wrap new VEK with master key
            val newEncryptedVek = CryptoEngine.encrypt(newVek, currentDerivedKey)
            dbHelper.setMetadata(KryptxDatabaseHelper.KEY_VERIFICATION_TOKEN, Base64.encodeToString(newEncryptedVek, Base64.NO_WRAP))

            // Update session active VEK
            sessionManager.unlock(newVek, isDecoy = false)

            if (isBiometricsConfigured()) {
                setupBiometrics()
            }

            SecureMemory.wipe(newVek)
            SecureMemory.wipe(currentDerivedKey)
            SecureMemory.wipe(salt)
            isAuditDirty = true
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            SecureMemory.wipe(newVek)
            SecureMemory.wipe(currentDerivedKey)
            SecureMemory.wipe(salt)
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to rotate encryption key: ${e.message}", e)
        }
    }

    override fun getItems(): Flow<List<VaultItem>> = sessionManager.isDecoy.flatMapLatest { isDecoy ->
        if (isDecoy) decoyDbHelper.itemsFlow else dbHelper.itemsFlow
    }

    override fun getTrashItems(): Flow<List<VaultItem>> = sessionManager.isDecoy.flatMapLatest { isDecoy ->
        if (isDecoy) decoyDbHelper.trashFlow else dbHelper.trashFlow
    }

    override suspend fun getItemById(id: String): VaultItem? = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext null
        val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
        targetDb.loadItemById(id, activeVek)
    }

    override suspend fun saveItem(item: VaultItem): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        val isDecoy = sessionManager.isDecoy.value
        return@withContext try {
            val targetDb = if (isDecoy) decoyDbHelper else dbHelper
            val success = targetDb.saveItem(item, activeVek)
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

    override suspend fun moveToTrash(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.moveToTrash(itemId, activeVek)
            if (success) {
                isAuditDirty = true
                KryptxResult.Success(Unit)
            } else {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to move item to trash")
            }
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to move item to trash", e)
        }
    }

    override suspend fun restoreFromTrash(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.restoreFromTrash(itemId, activeVek)
            if (success) {
                isAuditDirty = true
                KryptxResult.Success(Unit)
            } else {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to restore item from trash")
            }
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to restore item from trash", e)
        }
    }

    override suspend fun emptyTrash(): KryptxResult<Int> = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val deletedCount = targetDb.emptyTrash(activeVek)
            isAuditDirty = true
            KryptxResult.Success(deletedCount)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to empty trash", e)
        }
    }

    override suspend fun deleteItem(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.deleteItem(itemId)
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
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.toggleFavorite(itemId, activeVek)
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
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.recordItemUsage(itemId, activeVek)
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

        // 1.5. Password similarity detection (Levenshtein distance)
        var similarCount = 0
        val checkedPairs = mutableSetOf<Pair<String, String>>()
        for (i in loginItems.indices) {
            val itemA = loginItems[i]
            for (j in i + 1 until loginItems.size) {
                val itemB = loginItems[j]
                
                // Skip if exactly the same (handled by reuse detection)
                if (itemA.password == itemB.password) continue
                
                // Don't re-check if the password values are identical to a pair we already checked
                val pair = if (itemA.password < itemB.password) itemA.password to itemB.password else itemB.password to itemA.password
                if (checkedPairs.contains(pair)) continue
                checkedPairs.add(pair)

                val similarity = calculateSimilarity(itemA.password, itemB.password)
                if (similarity > 0.85) { // 85% similar
                    similarCount += 2 // We count both items
                    
                    issues.add(SecurityIssue(
                        id = "similar_${itemA.id}_to_${itemB.id}",
                        itemId = itemA.id,
                        itemTitle = itemA.title,
                        itemSubtitle = itemA.displaySubtitle,
                        severity = IssueSeverity.WARNING,
                        type = IssueType.SIMILAR_PASSWORD,
                        title = "Dangerously similar password",
                        description = "This password is highly similar to '${itemB.title}'. Tweaking existing passwords (e.g., adding a '1') is easily guessed by attackers.",
                        recommendation = "Generate a completely unique password."
                    ))
                    
                    issues.add(SecurityIssue(
                        id = "similar_${itemB.id}_to_${itemA.id}",
                        itemId = itemB.id,
                        itemTitle = itemB.title,
                        itemSubtitle = itemB.displaySubtitle,
                        severity = IssueSeverity.WARNING,
                        type = IssueType.SIMILAR_PASSWORD,
                        title = "Dangerously similar password",
                        description = "This password is highly similar to '${itemA.title}'. Tweaking existing passwords (e.g., adding a '1') is easily guessed by attackers.",
                        recommendation = "Generate a completely unique password."
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

        // 6. Compromised check via 100% Offline BreachChecker
        var compromisedCount = 0
        for (item in loginItems) {
            val breachStatus = com.kryptx.app.core.security.BreachChecker.checkPassword(
                item.password
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
            similarCount = similarCount,
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

            // Derive portable backup key via memory-hard Argon2id (RFC 9106).
            // Guarantees that backups can be restored 100% reliably on ANY device or fresh install.
            val encryptionKey = KeyDerivation.deriveKeyArgon2(exportPassword, salt)

            val pqcPubKeyBase64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_PQC_IDENTITY_PUBLIC_KEY)
            val isPostQuantum = pqcPubKeyBase64 != null

            val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
            val ciphertext = try {
                CryptoEngine.encrypt(plaintextBytes, encryptionKey)
            } finally {
                SecureMemory.wipe(plaintextBytes)
            }

            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val checksum = VaultExporter.computeSha256Checksum(ciphertextBase64)

            SecureMemory.wipe(encryptionKey)
            SecureMemory.wipe(salt)

            val header = BackupHeader(
                app = "Kryptx",
                version = "1.1.0",
                formatVersion = 2,
                exportedAt = System.currentTimeMillis(),
                isEncrypted = true,
                kdfAlgorithm = "Argon2id",
                kdfIterations = 0,
                saltBase64 = saltBase64,
                ivBase64 = "",
                isPostQuantum = isPostQuantum,
                pqcEncapsulationBase64 = null,
                checksumSha256 = checksum
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
            // Verify integrity checksum if present in header
            if (!payload.header.checksumSha256.isNullOrBlank()) {
                if (!VaultExporter.verifySha256Checksum(payload.ciphertextBase64, payload.header.checksumSha256)) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.IMPORT_PARSE_FAILED,
                        "Backup archive integrity checksum verification failed (corrupted or tampered payload)"
                    )
                }
            }

            val salt = Base64.decode(payload.header.saltBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)

            var decryptedBytes: ByteArray? = null

            // Strategy 1: Standard Argon2id derivation (v1.1.0+ portable backups)
            val argonKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
            try {
                decryptedBytes = CryptoEngine.decrypt(ciphertext, argonKey)
            } catch (_: Exception) {}
            SecureMemory.wipe(argonKey)

            // Strategy 2: Legacy PQC hybrid if same-device private key is available
            if (decryptedBytes == null && payload.header.isPostQuantum && !payload.header.pqcEncapsulationBase64.isNullOrBlank()) {
                val activeVek = sessionManager.getVaultKey()
                val privKeyCiphertext = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT)
                if (activeVek != null && privKeyCiphertext != null) {
                    try {
                        val classicalKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
                        val encryptedPrivKey = Base64.decode(privKeyCiphertext, Base64.NO_WRAP)
                        val privKeyBytes = CryptoEngine.decrypt(encryptedPrivKey, activeVek)
                        val encapsulationBytes = Base64.decode(payload.header.pqcEncapsulationBase64, Base64.NO_WRAP)
                        val pqcSharedSecret = PostQuantumEngine.decapsulate(
                            encapsulationBytes = encapsulationBytes,
                            privateKeyBytes = privKeyBytes,
                            classicalSaltOrSecret = classicalKey
                        )
                        val hybridKey = ByteArray(32) { i -> (pqcSharedSecret[i].toInt() xor classicalKey[i].toInt()).toByte() }
                        decryptedBytes = CryptoEngine.decrypt(ciphertext, hybridKey)
                        SecureMemory.wipe(privKeyBytes)
                        SecureMemory.wipe(pqcSharedSecret)
                        SecureMemory.wipe(classicalKey)
                        SecureMemory.wipe(hybridKey)
                    } catch (_: Exception) {}
                }
            }

            // Strategy 3: Legacy PBKDF2 iterations fallback
            if (decryptedBytes == null) {
                val iters = if (payload.header.kdfIterations > 0) payload.header.kdfIterations else KeyDerivation.DEFAULT_ITERATIONS
                val pbkdf2Key = KeyDerivation.deriveKey(importPassword, salt, iters)
                try {
                    decryptedBytes = CryptoEngine.decrypt(ciphertext, pbkdf2Key)
                } catch (_: Exception) {}
                SecureMemory.wipe(pbkdf2Key)
            }

            if (decryptedBytes == null) {
                return@withContext KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "Wrong import password or corrupted backup file")
            }

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
        decoyDbHelper.clearDatabaseKey()
    }

    override fun getDatabaseDiagnostics(): KryptxDatabaseHelper.DatabaseDiagnostics {
        return dbHelper.runDiagnostics()
    }

    override suspend fun vacuumDatabase(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dbHelper.vacuumDatabase()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to optimize database", e)
        }
    }

    // ==========================================
    // PQC Identity Key Pair
    // ==========================================

    override fun getPqcIdentityPublicKey(): ByteArray? {
        val base64 = dbHelper.getMetadata(KryptxDatabaseHelper.KEY_PQC_IDENTITY_PUBLIC_KEY) ?: return null
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun rotatePqcIdentityKeyPair(): KryptxResult<Unit> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
        return@withContext try {
            generateAndStorePqcIdentityKeyPair(activeVek)
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to rotate PQC identity key pair", e)
        }
    }

    // ==========================================
    // Passkey (FIDO2 / WebAuthn) — Registration & Assertion
    // ==========================================

    /**
     * Generates a fresh P-256 (ES256) WebAuthn credential using [PasskeyEngine], encrypts the
     * private key under the active VEK, and returns a [VaultItem] ready to be saved.
     *
     * The private key never leaves the device unencrypted. At assertion time, [assertPasskey]
     * decrypts it transiently, signs the challenge, and immediately wipes the plaintext key.
     */
    override suspend fun registerPasskey(
        rpId: String,
        rpName: String,
        userHandle: String,
        userName: String,
        challenge: ByteArray
    ): KryptxResult<VaultItem> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")

        return@withContext try {
            val registration = com.kryptx.app.core.crypto.PasskeyEngine.createPasskeyRegistration(
                rpId = rpId,
                userHandle = userHandle,
                userName = userName
            )

            // Encrypt the raw private key bytes under the VEK. Only the vault holder can decrypt.
            val encryptedPrivKey = CryptoEngine.encrypt(registration.rawPrivateKeyBytes, activeVek)
            val privKeyCiphertext = Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP)

            // Wipe the plaintext private key immediately after encryption.
            SecureMemory.wipe(registration.rawPrivateKeyBytes)

            val item = VaultItem(
                title = rpName,
                type = com.kryptx.app.core.model.ItemType.PASSKEY,
                username = userName,
                website = "https://$rpId",
                passkeyRpId = rpId,
                passkeyUserHandle = userHandle,
                passkeyCredentialId = registration.credentialId,
                passkeyPublicKeyCoseBase64 = registration.publicKeyCoseBase64,
                passkeyPrivateKeyCiphertext = privKeyCiphertext,
                passkeySignCount = 0
            )

            KryptxResult.Success(item)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to register passkey", e)
        }
    }

    /**
     * Produces a complete WebAuthn assertion signature for the given [VaultItem] passkey.
     * The private key is decrypted transiently from the VEK, used to sign, then immediately wiped.
     */
    override suspend fun assertPasskey(
        item: VaultItem,
        clientDataJsonBytes: ByteArray,
        rpId: String
    ): KryptxResult<com.kryptx.app.core.crypto.PasskeyEngine.PasskeyAssertionSignature> = withContext(Dispatchers.Default) {
        val activeVek = sessionManager.getVaultKey()
            ?: return@withContext KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")

        if (item.passkeyPrivateKeyCiphertext.isBlank()) {
            return@withContext KryptxResult.Error(
                KryptxErrorType.DATABASE_ERROR,
                "This passkey has no stored private key — it may have been created before full FIDO2 support."
            )
        }

        return@withContext try {
            // Transiently decrypt the private key — wiped immediately after signing.
            val encryptedPrivKey = Base64.decode(item.passkeyPrivateKeyCiphertext, Base64.NO_WRAP)
            val privateKeyBytes = CryptoEngine.decrypt(encryptedPrivKey, activeVek)

            val newSignCount = item.passkeySignCount + 1

            val assertion = try {
                com.kryptx.app.core.crypto.PasskeyEngine.signPasskeyAssertion(
                    rpId = rpId,
                    clientDataJsonBytes = clientDataJsonBytes,
                    privateKeyBytes = privateKeyBytes,
                    credentialId = item.passkeyCredentialId,
                    userHandle = item.passkeyUserHandle,
                    signCount = newSignCount
                )
            } finally {
                SecureMemory.wipe(privateKeyBytes)
            }

            // Increment sign count in the stored item to detect cloned authenticator attacks.
            val updatedItem = item.copy(
                passkeySignCount = newSignCount,
                updatedAt = System.currentTimeMillis()
            )
            saveItem(updatedItem)

            KryptxResult.Success(assertion)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to generate passkey assertion: ${e.message}", e)
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshtein(s1, s2)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(minOf(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
}