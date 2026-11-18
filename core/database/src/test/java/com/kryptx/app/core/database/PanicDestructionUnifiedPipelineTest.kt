package com.kryptx.app.core.database

import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class PanicDestructionUnifiedPipelineTest {

    private class FakeDatabaseHelper : KryptxDatabaseHelper(Mockito.mock(Context::class.java)) {
        var clearAllDataCalled = false
        override fun clearAllData() {
            clearAllDataCalled = true
        }
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
    }

    @Test
    fun testPanicSelfDestructExecutesUnifiedDestructionPipeline() = runTest {
        var emergencyWipeInvoked = false
        val repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager,
            onEmergencyWipe = { emergencyWipeInvoked = true }
        )

        // Precondition: vault is unlocked
        val dummyKey = ByteArray(32) { 42 }
        sessionManager.unlock(dummyKey)
        assertTrue("Precondition: vault must be unlocked before panic destruct", sessionManager.isUnlocked.value)

        // Execute panic self-destruct
        repository.triggerPanicSelfDestruct()

        // Postcondition 1: In-memory session key locked and wiped
        assertFalse("Session must be locked after panic destruct", sessionManager.isUnlocked.value)

        // Postcondition 2: Complete Keystore purge invoked (biometric, ECDH, and metadata MasterKey)
        Mockito.verify(keystoreManager).wipeAllKeystoreAliases()

        // Postcondition 3: Database clearAllData invoked for both primary and decoy
        assertTrue("dbHelper.clearAllData must be invoked", dbHelper.clearAllDataCalled)
        assertTrue("decoyDbHelper.clearAllData must be invoked", decoyDbHelper.clearAllDataCalled)

        // Postcondition 4: Central emergency self-destruct wipe must be invoked
        assertTrue("Central emergency self-destruct wipe must be invoked", emergencyWipeInvoked)
    }
}
