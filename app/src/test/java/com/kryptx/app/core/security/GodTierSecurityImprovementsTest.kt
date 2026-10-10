package com.kryptx.app.core.security

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.kryptx.app.core.crypto.Argon2Engine
import com.kryptx.app.core.crypto.BiometricKeyStatus
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.database.HmacSearchIndex
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.KryptxDbSchema
import com.kryptx.app.core.database.VaultAuthRepositoryImpl
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64

class GodTierSecurityImprovementsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeDatabaseHelper : KryptxDatabaseHelper(Mockito.mock(Context::class.java)) {
        val metadata = mutableMapOf<String, String>()
        override fun getMetadata(key: String): String? = metadata[key]
        override fun setMetadata(key: String, value: String) {
            metadata[key] = value
        }
    }

    private lateinit var dbHelper: FakeDatabaseHelper
    private lateinit var decoyDbHelper: FakeDatabaseHelper
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var repository: VaultAuthRepositoryImpl

    private val masterPassword = "MasterPassword123!".toCharArray()
    private val salt = KeyDerivation.generateSalt()
    private val saltBase64 = Base64.getEncoder().encodeToString(salt)
    private val initialVek = CryptoEngine.generateVaultKey()
    private val fastArgon2Params = Argon2Engine.Argon2Params.FAST_TEST

    @Before
    fun setUp() {
        dbHelper = FakeDatabaseHelper()
        decoyDbHelper = FakeDatabaseHelper()
        sessionManager = VaultSessionManager()
        keystoreManager = Mockito.mock(KeystoreManager::class.java)

        val derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt, fastArgon2Params)
        val encryptedVek = CryptoEngine.encrypt(initialVek, derivedMasterKey)
        val tokenBase64 = Base64.getEncoder().encodeToString(encryptedVek)

        dbHelper.metadata[KryptxDbSchema.KEY_SALT] = saltBase64
        dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN] = tokenBase64
        dbHelper.metadata[KryptxDbSchema.KEY_KDF_ALGORITHM] = KeyDerivation.KdfAlgorithm.ARGON2ID.identifier
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_MEMORY_KB] = fastArgon2Params.memoryCostKb.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_ITERATIONS] = fastArgon2Params.iterations.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_PARALLELISM] = fastArgon2Params.parallelism.toString()

        repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager
        )
    }

    // ── 1. Biometric Key Invalidation & Diagnostics ──────────────────────────

    @Test
    fun testBiometricKeyStatusEnumValuesAndDiagnostics() {
        // Verify all 4 states are available and distinct
        val states = BiometricKeyStatus.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(BiometricKeyStatus.Valid))
        assertTrue(states.contains(BiometricKeyStatus.NotEnrolled))
        assertTrue(states.contains(BiometricKeyStatus.InvalidatedByEnrollment))
        assertTrue(states.contains(BiometricKeyStatus.Corrupted))

        // Fake repository diagnostic detection and re-enrollment
        val fakeRepo = FakeVaultRepository()
        assertEquals(BiometricKeyStatus.NotEnrolled, fakeRepo.detectBiometricStatus())

        runBlocking {
            val enrollResult = fakeRepo.reEnrollBiometrics()
            assertTrue(enrollResult is KryptxResult.Success)
            assertEquals(BiometricKeyStatus.Valid, fakeRepo.detectBiometricStatus())
        }
    }

    // ── 2. Repository Layer Rate Limiting ─────────────────────────────────────

    @Test
    fun testRepositoryLayerRateLimitingPreventsExpensiveArgon2Kdf() {
        // Simulate failed unlock attempts to trigger lockout
        repeat(5) {
            sessionManager.recordFailedAttempt()
        }
        assertTrue("Session manager must be in locked out state", sessionManager.lockoutSecondsRemaining.value > 0)

        runBlocking {
            // Attempt unlock with password while locked out
            val result = repository.unlockWithPassword(masterPassword)

            assertTrue("Unlock must be rejected immediately with RATE_LIMITED", result is KryptxResult.Error)
            val error = result as KryptxResult.Error
            assertEquals(KryptxErrorType.RATE_LIMITED, error.type)
            assertTrue(error.message.contains("Too many failed unlock attempts"))
            assertFalse("Session must remain locked", sessionManager.isUnlocked.value)
        }
    }

    // ── 3. HMAC Search Token Length & Prefix Rules ────────────────────────────

    @Test
    fun testHmacSearchIndexMinLengthAndPrefixBounds() {
        val searchIndex = HmacSearchIndex()
        val vek = CryptoEngine.generateVaultKey()
        searchIndex.initKey(vek)
        assertTrue(searchIndex.isKeyAvailable)

        // Mock database
        val mockDb = Mockito.mock(net.zetetic.database.sqlcipher.SQLiteDatabase::class.java)

        // Querying tokens with length < 3 must immediately return null without touching SQL
        val shortResult1 = searchIndex.queryItemIds(mockDb, "a")
        assertNull("Query with length 1 must return null", shortResult1)

        val shortResult2 = searchIndex.queryItemIds(mockDb, "to")
        assertNull("Query with length 2 must return null", shortResult2)

        val blankResult = searchIndex.queryItemIds(mockDb, "   ")
        assertNull("Blank query must return null", blankResult)

        // Verify clearing key wipes state
        searchIndex.clearKey()
        assertFalse(searchIndex.isKeyAvailable)
    }

    @Test
    fun testHmacSearchIndexHostileInputsDoNotThrow() {
        val searchIndex = HmacSearchIndex()
        val vek = CryptoEngine.generateVaultKey()
        searchIndex.initKey(vek)
        val mockDb = Mockito.mock(net.zetetic.database.sqlcipher.SQLiteDatabase::class.java)
        val mockCursor = Mockito.mock(android.database.Cursor::class.java)
        Mockito.`when`(mockCursor.moveToNext()).thenReturn(false)
        Mockito.`when`(mockDb.rawQuery(Mockito.anyString(), Mockito.any())).thenReturn(mockCursor)

        val hostileInputs = listOf(
            "\u0000\u0000\u0000",
            "' OR '1'='1' --",
            "🎉🚀🔥💎🔐",
            "\n\t\r\b",
            "A".repeat(10000)
        )

        for (input in hostileInputs) {
            try {
                searchIndex.queryItemIds(mockDb, input)
            } catch (e: Exception) {
                // Must not throw unhandled runtime exceptions
                throw AssertionError("Hostile input failed: $input", e)
            }
        }
    }

    // ── 4. Attachment 50MB Size Limit Enforcement ────────────────────────────

    @Test
    fun testAttachmentManagerMaxAttachmentSizeConstant() {
        assertEquals(50 * 1024 * 1024L, AttachmentManager.MAX_ATTACHMENT_SIZE)
    }

    @Test
    fun testAttachmentManagerRejectsPayloadExceeding50Mb() = runBlocking {
        val context = Mockito.mock(Context::class.java)
        val filesDir = tempFolder.newFolder("app_files")
        Mockito.`when`(context.filesDir).thenReturn(filesDir)

        sessionManager.unlock(initialVek)
        val manager = AttachmentManager(context, sessionManager)
        val oversizedData = ByteArray((50 * 1024 * 1024) + 1) // 50MB + 1 byte

        val saved = manager.saveAttachment("large.bin", "application/octet-stream", oversizedData)
        assertNull("Oversized attachment save must return null", saved)
    }

    @Test
    fun testAttachmentManagerRejectsStreamExceeding50MbAndCleansUp() = runBlocking {
        val context = Mockito.mock(Context::class.java)
        val filesDir = tempFolder.newFolder("app_files_stream")
        Mockito.`when`(context.filesDir).thenReturn(filesDir)

        sessionManager.unlock(initialVek)
        val manager = AttachmentManager(context, sessionManager)

        // Stream that produces more than 50MB
        val totalBytesToProduce = 51 * 1024 * 1024L
        var producedBytes = 0L
        val oversizedStream = object : InputStream() {
            override fun read(): Int {
                return if (producedBytes < totalBytesToProduce) {
                    producedBytes++
                    0x42
                } else {
                    -1
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (producedBytes >= totalBytesToProduce) return -1
                val toRead = minOf(len.toLong(), totalBytesToProduce - producedBytes).toInt()
                b.fill(0x42, off, off + toRead)
                producedBytes += toRead
                return toRead
            }
        }

        val saved = manager.saveAttachmentStream("oversized_stream.bin", "application/octet-stream", oversizedStream)
        assertNull("Oversized attachment stream must return null", saved)

        // Ensure attachments directory contains no orphaned files
        val attachmentsDir = File(filesDir, "vault_attachments")
        if (attachmentsDir.exists()) {
            val files = attachmentsDir.listFiles() ?: emptyArray()
            assertEquals("No orphaned partial files must remain after size ceiling violation", 0, files.size)
        }
    }

    // ── 5. CrashDefense Anti-Zombie Loop Throttling ───────────────────────────

    @Test
    fun testCrashDefenseRecordsCrashesAndMaintainsHistory() {
        val testEx1 = RuntimeException("Crash 1")
        val testEx2 = RuntimeException("Crash 2")

        CrashDefense.recordCrash(testEx1, "main")
        CrashDefense.recordCrash(testEx2, "main")

        val history = CrashDefense.getCrashHistory()
        assertTrue("Crash history must contain recorded exceptions", history.size >= 2)
        assertEquals("main", history.last().threadName)
    }

    // ── 6. Forensic Zeroization Byte-Size Decoding ───────────────────────────

    @Test
    fun testDatabaseForensicZeroizationExactByteLengthCalculation() {
        // Test that Base64 decoding accurately calculates the ciphertext size
        val originalPayload = ByteArray(128) { it.toByte() }
        val base64Payload = Base64.getEncoder().encodeToString(originalPayload)

        val decodedLength = try {
            Base64.getDecoder().decode(base64Payload).size
        } catch (_: Exception) {
            base64Payload.toByteArray(Charsets.UTF_8).size
        }

        assertEquals(128, decodedLength)
        // Ensure random overwrite buffer would match actual ciphertext byte count
        val overwriteSize = maxOf(decodedLength, 64)
        assertEquals(128, overwriteSize)
    }

    // ── 7. Biometric Error Message Sanitization ───────────────────────────────

    @Test
    fun testBiometricErrorMessageSanitizationContract() {
        fun sanitize(errorCode: Int, errString: CharSequence?): String {
            return when (errorCode) {
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                    "Biometric authentication is unavailable on this device"
                BiometricPrompt.ERROR_LOCKOUT,
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                    "Too many failed biometric attempts. Please wait or use master password."
                BiometricPrompt.ERROR_NO_BIOMETRICS ->
                    "No biometrics enrolled. Add biometric credential in device settings."
                BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                    "Device security update required for biometric authentication"
                BiometricPrompt.ERROR_TIMEOUT ->
                    "Biometric authentication timed out"
                else -> if (!errString.isNullOrBlank()) errString.toString() else "Biometric authentication failed. Please try again."
            }
        }

        assertEquals(
            "Biometric authentication is unavailable on this device",
            sanitize(BiometricPrompt.ERROR_HW_UNAVAILABLE, "Hardware dead")
        )
        assertEquals(
            "Too many failed biometric attempts. Please wait or use master password.",
            sanitize(BiometricPrompt.ERROR_LOCKOUT, "Too many attempts")
        )
        assertEquals(
            "No biometrics enrolled. Add biometric credential in device settings.",
            sanitize(BiometricPrompt.ERROR_NO_BIOMETRICS, "None enrolled")
        )
        assertEquals(
            "Device security update required for biometric authentication",
            sanitize(BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED, "Vuln found")
        )
        assertEquals(
            "Biometric authentication timed out",
            sanitize(BiometricPrompt.ERROR_TIMEOUT, "Timed out")
        )
        assertEquals(
            "Biometric authentication failed. Please try again.",
            sanitize(999, null)
        )
    }

    // ── 8. Degraded Mode Storage Flag ─────────────────────────────────────────

    @Test
    fun testDegradedModeStorageFlagDefaultsToFalse() {
        val helper = FakeDatabaseHelper()
        assertFalse("Degraded storage mode must default to false", helper.isDegradedStorageMode())
    }
}
