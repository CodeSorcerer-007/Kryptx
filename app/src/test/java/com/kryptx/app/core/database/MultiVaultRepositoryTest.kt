package com.kryptx.app.core.database

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MultiVaultRepositoryTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private lateinit var repository: FakeVaultRepository

    @Before
    fun setUp() = runTest(testDispatcher) {
        repository = FakeVaultRepository()
        repository.setupNewVault("MasterPassword123!".toCharArray())
    }

    @Test
    fun testDefaultActiveVaultIsPersonal() {
        val activeVault = repository.getActiveVaultId()
        assertEquals("personal", activeVault)
    }

    @Test
    fun testSwitchVaultWorkspace() = runTest(testDispatcher) {
        val switchResult = repository.switchVault("work")
        assertTrue(switchResult.isSuccess)
        assertEquals("work", repository.getActiveVaultId())

        val switchBackResult = repository.switchVault("personal")
        assertTrue(switchBackResult.isSuccess)
        assertEquals("personal", repository.getActiveVaultId())
    }

    @Test
    fun testHardwareKeyEnrollmentLifecycle() = runTest(testDispatcher) {
        assertFalse(repository.isHardwareKeyEnrolled())

        val challenge = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val hardwareSecret = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

        val enrollResult = repository.enrollHardwareKey(
            label = "YubiKey 5C NFC",
            uidHash = "mockHash123",
            challenge = challenge,
            hardwareSecret = hardwareSecret,
            masterPassword = "MasterPassword123!".toCharArray()
        )

        assertTrue(enrollResult.isSuccess)
        assertTrue(repository.isHardwareKeyEnrolled())
        assertEquals("YubiKey 5C NFC", repository.getHardwareKeyLabel())
        assertEquals("mockHash123", repository.getHardwareKeyUidHash())
        assertNotNull(repository.getHardwareKeyChallenge())

        // Test unlock with hardware key
        val unlockSuccess = repository.unlockWithHardwareKey(
            "MasterPassword123!".toCharArray(),
            hardwareSecret
        )
        assertTrue(unlockSuccess.isSuccess)

        // Test remove hardware key
        val removeResult = repository.removeHardwareKey("MasterPassword123!".toCharArray())
        assertTrue(removeResult.isSuccess)
        assertFalse(repository.isHardwareKeyEnrolled())
    }

    @Test
    fun testDatabaseDiagnosticsReport() {
        val diagnostics = repository.getDatabaseDiagnostics()
        assertTrue(diagnostics.isIntegrityOk)
        assertEquals("ok", diagnostics.integrityReport)
        assertEquals(4096, diagnostics.pageSizeBytes)
        assertTrue(diagnostics.pageCount > 0)
    }

    @Test
    fun testCustomWorkspaceIdentifiers() = runTest(testDispatcher) {
        val customWorkspaces = listOf("finance", "crypto_ledger", "secure_notes")
        for (ws in customWorkspaces) {
            val result = repository.switchVault(ws)
            assertTrue(result.isSuccess)
            assertEquals(ws, repository.getActiveVaultId())
        }
    }

    @Test
    fun testVacuumDatabaseSuccess() = runTest(testDispatcher) {
        val vacuumResult = repository.vacuumDatabase()
        assertTrue(vacuumResult.isSuccess)
    }

    @Test
    fun testResetVaultClearsState() = runTest(testDispatcher) {
        repository.saveItem(VaultItem(title = "ResetTestItem", username = "user", password = "pass"))
        assertEquals(1, repository.getItems().first().size)

        repository.resetVault()
        assertFalse(repository.hasVault())
        assertEquals(0, repository.getItems().first().size)
    }

    @Test
    fun testUnlockWithWrongPasswordFails() = runTest(testDispatcher) {
        val result = repository.unlockWithPassword("WrongPassword!".toCharArray())
        assertTrue(result.isError)
    }

    @Test
    fun testUnlockWithCorrectPasswordSucceeds() = runTest(testDispatcher) {
        val result = repository.unlockWithPassword("MasterPassword123!".toCharArray())
        assertTrue(result.isSuccess)
    }
}
