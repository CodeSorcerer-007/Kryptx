package com.kryptx.app.core.database

import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.security.IVaultSessionManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PanicDestructionUnifiedPipelineTest {

    private class FakeDatabaseHelper : KryptxDatabaseHelper(Mockito.mock(Context::class.java)) {
        var clearAllDataCalled = false
        var clearAllDataResult = true
        val metadata = mutableMapOf<String, String>()
        var setMetadataResult = true

        override fun clearAllData(): Boolean {
            clearAllDataCalled = true
            return clearAllDataResult
        }

        override fun getMetadata(key: String): String? = metadata[key]

        override fun setMetadata(key: String, value: String): Boolean {
            if (!setMetadataResult) return false
            metadata[key] = value
            return true
        }
    }

    private class FakeFailingSessionManager(
        private val failureException: Exception = SecurityException("Strict memory locking simulated failure")
    ) : IVaultSessionManager {
        var lockCalled = false
        var unlockCalled = false

        override val isUnlocked = MutableStateFlow(false)
        override val isDecoy = MutableStateFlow(false)
        override val isLockedDueToTimeout = MutableStateFlow(false)
        override val failedAttempts = MutableStateFlow(0)
        override val lockoutSecondsRemaining = MutableStateFlow(0)

        override fun unlock(vaultKey: ByteArray, isDecoy: Boolean) {
            unlockCalled = true
            throw failureException
        }

        override fun lock(isTimeout: Boolean) {
            lockCalled = true
        }

        override fun getVaultKey(): ByteArray? = null
        override fun isSessionKeyMemoryLocked(): Boolean = false
        override fun recordActivity() {}
        override fun recordFailedAttempt(): Int = 1
        override fun addLockListener(listener: () -> Unit) = java.io.Closeable {}
        override fun removeLockListener(listener: () -> Unit) {}
        override fun addUnlockListener(listener: () -> Unit) = java.io.Closeable {}
        override fun removeUnlockListener(listener: () -> Unit) {}
        override fun addFailedAttemptListener(listener: (Int) -> Unit) = java.io.Closeable {}
        override fun removeFailedAttemptListener(listener: (Int) -> Unit) {}
        override fun clearAllListeners() {}
        override fun setAutoLockTimeout(timeout: VaultSessionManager.AutoLockTimeout) {}
        override fun setLockOnBackground(lock: Boolean) {}
        override fun onAppBackgrounded() {}
        override fun onAppForegrounded() {}
        override fun setPickerActive(active: Boolean) {}
    }

    private lateinit var dbHelper: FakeDatabaseHelper
    private lateinit var decoyDbHelper: FakeDatabaseHelper
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var keystoreManager: KeystoreManager

    @Before
    fun setUp() {
        dbHelper = FakeDatabaseHelper()
        decoyDbHelper = FakeDatabaseHelper()
        sessionManager = VaultSessionManager()
        keystoreManager = Mockito.mock(KeystoreManager::class.java)
        Mockito.`when`(keystoreManager.wipeAllKeystoreAliases()).thenReturn(true)
        Mockito.`when`(keystoreManager.removeBiometricKey()).thenReturn(true)
    }

    @Test
    fun testPanicSelfDestructExecutesUnifiedDestructionPipeline() = runTest {
        var emergencyWipeInvoked = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = {
                emergencyWipeInvoked = true
                com.kryptx.app.core.model.DestructionResult.Success
            }
        )

        // Precondition: vault is unlocked
        val dummyKey = ByteArray(32) { 42 }
        sessionManager.unlock(dummyKey)
        assertTrue("Precondition: vault must be unlocked before panic destruct", sessionManager.isUnlocked.value)

        // Execute panic self-destruct
        val result = repository.triggerPanicSelfDestruct()

        // Postcondition 1: In-memory session key locked and wiped
        assertFalse("Session must be locked after panic destruct", sessionManager.isUnlocked.value)

        // Postcondition 2: Complete Keystore purge invoked (biometric, ECDH, and metadata MasterKey)
        Mockito.verify(keystoreManager).wipeAllKeystoreAliases()

        // Postcondition 3: Database clearAllData invoked for both primary and decoy
        assertTrue("dbHelper.clearAllData must be invoked", dbHelper.clearAllDataCalled)
        assertTrue("decoyDbHelper.clearAllData must be invoked", decoyDbHelper.clearAllDataCalled)

        // Postcondition 4: Central emergency self-destruct wipe must be invoked
        assertTrue("Central emergency self-destruct wipe must be invoked", emergencyWipeInvoked)

        // Postcondition 5: Full success reported
        assertTrue("Panic self-destruct must report DestructionResult.Success", result is com.kryptx.app.core.model.DestructionResult.Success)
    }

    @Test
    fun testPanicSelfDestructReportsIncompleteWhenKeystoreFails() = runTest {
        Mockito.`when`(keystoreManager.wipeAllKeystoreAliases()).thenReturn(false)
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.triggerPanicSelfDestruct()
        assertTrue("Must report DestructionResult.Incomplete when Keystore purge fails", result is com.kryptx.app.core.model.DestructionResult.Incomplete)
        val incomplete = result as com.kryptx.app.core.model.DestructionResult.Incomplete
        assertFalse("keystorePurged must be false", incomplete.keystorePurged)
    }

    @Test
    fun testPanicSelfDestructReportsIncompleteWhenEmergencyWipeCallbackMissing() = runTest {
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = null // Missing callback
        )

        val result = repository.triggerPanicSelfDestruct()
        assertTrue("Must report DestructionResult.Incomplete when emergency wipe callback is missing", result is com.kryptx.app.core.model.DestructionResult.Incomplete)
        val incomplete = result as com.kryptx.app.core.model.DestructionResult.Incomplete
        assertFalse("databasesPurged must be false when wipe pipeline is missing", incomplete.databasesPurged)
    }

    @Test
    fun testSetupNewVaultRollsBackWhenSessionUnlockFails() = runTest {
        val failingSessionManager = FakeFailingSessionManager()

        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = failingSessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.setupNewVault("TestPassword123!".toCharArray())
        assertTrue("Setup must return error on failed session lock", result is com.kryptx.app.core.model.KryptxResult.Error)
        assertTrue("Database helper must clear all data during rollback", dbHelper.clearAllDataCalled)
        assertTrue("Decoy database helper must clear all data during rollback", decoyDbHelper.clearAllDataCalled)
        Mockito.verify(keystoreManager, Mockito.atLeastOnce()).wipeAllKeystoreAliases()
        assertTrue("Session manager lock must be invoked during rollback", failingSessionManager.lockCalled)
    }

    @Test
    fun testSetupNewVaultReportsIncompleteRollbackWhenKeystoreCleanupFails() = runTest {
        val failingSessionManager = FakeFailingSessionManager()

        Mockito.`when`(keystoreManager.wipeAllKeystoreAliases()).thenReturn(false)

        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = failingSessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.setupNewVault("TestPassword123!".toCharArray())
        assertTrue("Setup must return error", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must report incomplete rollback", error.message.contains("Rollback incomplete"))
        assertTrue("Error message must indicate keystore wipe failed", error.message.contains("keystore wipe failed"))
    }

    @Test
    fun testResetVaultReturnsSuccessWhenAllCleanupsSucceed() = runTest {
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.resetVault()
        assertTrue("resetVault must return Success when all cleanups succeed", result is com.kryptx.app.core.model.KryptxResult.Success)
        assertTrue("dbHelper.clearAllData must be called", dbHelper.clearAllDataCalled)
        assertTrue("decoyDbHelper.clearAllData must be called", decoyDbHelper.clearAllDataCalled)
    }

    @Test
    fun testResetVaultReturnsErrorWhenPrimaryDbCleanupFails() = runTest {
        dbHelper.clearAllDataResult = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.resetVault()
        assertTrue("resetVault must return Error when primary DB clear fails", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must indicate primary DB cleanup failed", error.message.contains("Primary database/storage cleanup incomplete"))
    }

    @Test
    fun testResetVaultReturnsErrorWhenBiometricKeyRemovalFails() = runTest {
        Mockito.`when`(keystoreManager.removeBiometricKey()).thenReturn(false)
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.resetVault()
        assertTrue("resetVault must return Error when biometric key removal fails", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must specify biometric removal failure", error.message.contains("Biometric key removal incomplete"))
    }

    @Test
    fun testDisableBiometricsReturnsErrorWhenKeystoreRemovalFails() = runTest {
        Mockito.`when`(keystoreManager.removeBiometricKey()).thenReturn(false)
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.disableBiometrics()
        assertTrue("disableBiometrics must return Error when keystore removal fails", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must explain keystore failure", error.message.contains("Failed to delete biometric key"))
    }

    @Test
    fun testDisableBiometricsReturnsSuccessWhenKeystoreRemovalSucceeds() = runTest {
        Mockito.`when`(keystoreManager.removeBiometricKey()).thenReturn(true)
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.disableBiometrics()
        assertTrue("disableBiometrics must return Success when keystore removal succeeds", result is com.kryptx.app.core.model.KryptxResult.Success)
    }

    @Test
    fun testDisableBiometricsReturnsErrorWhenMetadataCommitFails() = runTest {
        Mockito.`when`(keystoreManager.removeBiometricKey()).thenReturn(true)
        dbHelper.setMetadataResult = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.disableBiometrics()
        assertTrue("disableBiometrics must return Error when metadata commit fails", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must explain metadata write failure", error.message.contains("metadata"))
    }

    @Test
    fun testResetVaultReturnsErrorWhenDecoyDbCleanupFails() = runTest {
        decoyDbHelper.clearAllDataResult = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.resetVault()
        assertTrue("resetVault must return Error when decoy DB clear fails", result is com.kryptx.app.core.model.KryptxResult.Error)
        val error = result as com.kryptx.app.core.model.KryptxResult.Error
        assertTrue("Error message must indicate decoy DB cleanup failed", error.message.contains("Decoy database/storage cleanup incomplete"))
    }

    @Test
    fun testPanicSelfDestructReportsIncompleteWhenPrimaryDbCleanupFails() = runTest {
        dbHelper.clearAllDataResult = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.triggerPanicSelfDestruct()
        assertTrue("Must report Incomplete when primary DB cleanup fails", result is com.kryptx.app.core.model.DestructionResult.Incomplete)
        val incomplete = result as com.kryptx.app.core.model.DestructionResult.Incomplete
        assertFalse("databasesPurged must be false", incomplete.databasesPurged)
        assertTrue("Error message must specify primary failure", incomplete.errorMessage.contains("primary database purge failed"))
    }

    @Test
    fun testPanicSelfDestructReportsIncompleteWhenDecoyDbCleanupFails() = runTest {
        decoyDbHelper.clearAllDataResult = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { com.kryptx.app.core.model.DestructionResult.Success }
        )

        val result = repository.triggerPanicSelfDestruct()
        assertTrue("Must report Incomplete when decoy DB cleanup fails", result is com.kryptx.app.core.model.DestructionResult.Incomplete)
        val incomplete = result as com.kryptx.app.core.model.DestructionResult.Incomplete
        assertFalse("databasesPurged must be false", incomplete.databasesPurged)
        assertTrue("Error message must specify decoy failure", incomplete.errorMessage.contains("decoy database purge failed"))
    }
}
