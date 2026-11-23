package com.kryptx.app.core.database

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.migration.VaultExporter
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.Base64

class PostQuantumBackupTest {

    private class FakeDatabaseHelper : KryptxDatabaseHelper(Mockito.mock(android.content.Context::class.java)) {
        val metadata = mutableMapOf<String, String>()
        val savedItems = mutableListOf<VaultItem>()

        override fun getMetadata(key: String): String? = metadata[key]
        override fun setMetadata(key: String, value: String): Boolean {
            metadata[key] = value
            return true
        }
        override fun setMetadataBatch(entries: Map<String, String>): Boolean {
            metadata.putAll(entries)
            return true
        }
        override suspend fun loadAllItems(vaultKey: ByteArray): List<VaultItem> = savedItems.toList()
        override suspend fun saveItemsBatch(items: List<VaultItem>, vaultKey: ByteArray): Int {
            savedItems.addAll(items)
            return items.size
        }
    }

    private lateinit var dbHelper: FakeDatabaseHelper
    private lateinit var decoyDbHelper: FakeDatabaseHelper
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var repository: VaultCrudRepositoryImpl
    private val testVek = ByteArray(32) { (it + 1).toByte() }

    @Before
    fun setUp() {
        dbHelper = FakeDatabaseHelper()
        decoyDbHelper = FakeDatabaseHelper()
        sessionManager = VaultSessionManager()
        sessionManager.unlock(testVek)

        repository = VaultCrudRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager
        )

        // Seed sample vault items
        dbHelper.savedItems.add(
            VaultItem(
                id = "item-1",
                type = ItemType.LOGIN,
                title = "ProtonMail",
                username = "alice@kryptx.io",
                password = "QuantumPassword#2026!"
            )
        )
        dbHelper.savedItems.add(
            VaultItem(
                id = "item-2",
                type = ItemType.SECURE_NOTE,
                title = "Recovery Seed",
                notes = "Seed phrase protected by ML-KEM-768"
            )
        )
    }

    @Test
    fun testExportEncryptedBackupGeneratesGenuinePostQuantumKEMAndDSAArtifacts() = runBlocking {
        val exportPassword = "StrongExportPassword123!".toCharArray()

        val exportResult = repository.exportEncryptedBackup(exportPassword)
        assertTrue("Export must succeed", exportResult is KryptxResult.Success)

        val payload = (exportResult as KryptxResult.Success).data
        val header = payload.header

        // 1. Verify Post-Quantum flags and fields are populated
        assertTrue("Header must indicate isPostQuantum = true", header.isPostQuantum)
        assertNotNull("ML-KEM encapsulation must not be null", header.pqcEncapsulationBase64)
        assertTrue("Encapsulation must not be blank", header.pqcEncapsulationBase64!!.isNotBlank())
        assertNotNull("ML-DSA signature must not be null", header.pqcSignatureBase64)
        assertNotNull("ML-DSA signer public key must not be null", header.pqcSignerPublicKeyBase64)
        assertNotNull("Encrypted KEM private key must not be null", header.pqcEncryptedPrivateKeyBase64)

        // 2. Verify ML-DSA-65 post-quantum digital signature
        val encapsulationBytes = Base64.getDecoder().decode(header.pqcEncapsulationBase64)
        val ciphertextBytes = Base64.getDecoder().decode(payload.ciphertextBase64)
        val signatureBytes = Base64.getDecoder().decode(header.pqcSignatureBase64)
        val signerPubBytes = Base64.getDecoder().decode(header.pqcSignerPublicKeyBase64)

        val payloadToVerify = encapsulationBytes + ciphertextBytes
        val isSignatureValid = PostQuantumEngine.verifySignature(payloadToVerify, signatureBytes, signerPubBytes)
        assertTrue("ML-DSA-65 post-quantum signature must be valid", isSignatureValid)

        // 3. Verify roundtrip import
        dbHelper.savedItems.clear()
        val importResult = repository.importEncryptedBackup(payload, exportPassword)
        assertTrue("Import must succeed", importResult is KryptxResult.Success)
        assertEquals(2, (importResult as KryptxResult.Success).data)
        assertEquals(2, dbHelper.savedItems.size)
        assertEquals("ProtonMail", dbHelper.savedItems[0].title)
    }

    @Test
    fun testImportEncryptedBackupRejectsTamperedMLDSASignature() = runBlocking {
        val exportPassword = "StrongExportPassword123!".toCharArray()
        val payload = (repository.exportEncryptedBackup(exportPassword) as KryptxResult.Success).data

        // Tamper with the ML-DSA signature
        val rawSig = Base64.getDecoder().decode(payload.header.pqcSignatureBase64)
        rawSig[0] = (rawSig[0].toInt() xor 0xFF).toByte()
        val tamperedSigBase64 = Base64.getEncoder().encodeToString(rawSig)

        val tamperedHeader = payload.header.copy(pqcSignatureBase64 = tamperedSigBase64)
        val tamperedPayload = payload.copy(header = tamperedHeader)

        val importResult = repository.importEncryptedBackup(tamperedPayload, exportPassword)
        assertTrue("Import must fail when ML-DSA signature is tampered", importResult is KryptxResult.Error)
    }

    @Test
    fun testImportEncryptedBackupRejectsTamperedCiphertext() = runBlocking {
        val exportPassword = "StrongExportPassword123!".toCharArray()
        val payload = (repository.exportEncryptedBackup(exportPassword) as KryptxResult.Success).data

        val rawCipher = Base64.getDecoder().decode(payload.ciphertextBase64)
        rawCipher[rawCipher.size - 1] = (rawCipher[rawCipher.size - 1].toInt() xor 0x55).toByte()
        val tamperedCipherBase64 = Base64.getEncoder().encodeToString(rawCipher)

        val tamperedPayload = payload.copy(ciphertextBase64 = tamperedCipherBase64)

        val importResult = repository.importEncryptedBackup(tamperedPayload, exportPassword)
        assertTrue("Import must fail when ciphertext is tampered", importResult is KryptxResult.Error)
    }

    @Test
    fun testExportAndImportSignedPostQuantumArchiveRoundtrip() = runBlocking {
        val exportResult = repository.exportSignedPostQuantumArchive()
        assertTrue("Export KPQC archive must succeed", exportResult is KryptxResult.Success)

        val archiveBytes = (exportResult as KryptxResult.Success).data
        assertTrue("Archive must start with KPQC magic", archiveBytes.size >= 4)
        assertEquals('K'.code.toByte(), archiveBytes[0])
        assertEquals('P'.code.toByte(), archiveBytes[1])
        assertEquals('Q'.code.toByte(), archiveBytes[2])
        assertEquals('C'.code.toByte(), archiveBytes[3])

        dbHelper.savedItems.clear()
        val importResult = repository.importSignedPostQuantumArchive(archiveBytes)
        assertTrue("Import KPQC archive must succeed", importResult is KryptxResult.Success)
        assertEquals(2, (importResult as KryptxResult.Success).data)
        assertEquals(2, dbHelper.savedItems.size)
    }

    @Test
    fun testImportSignedPostQuantumArchiveRejectsTamperedArchive() = runBlocking {
        val archiveBytes = (repository.exportSignedPostQuantumArchive() as KryptxResult.Success).data
        val tamperedBytes = archiveBytes.clone()
        tamperedBytes[tamperedBytes.size - 10] = (tamperedBytes[tamperedBytes.size - 10].toInt() xor 0xAA).toByte()

        val importResult = repository.importSignedPostQuantumArchive(tamperedBytes)
        assertTrue("Importing tampered KPQC archive must fail", importResult is KryptxResult.Error)
    }

    @Test
    fun testBackwardCompatibilityWithClassicalArgon2Backup() = runBlocking {
        val password = "ClassicalPassword123!".toCharArray()
        val salt = KeyDerivation.generateSalt()
        val argonKey = KeyDerivation.deriveKeyArgon2(password, salt)

        val items = listOf(
            VaultItem(id = "legacy-1", title = "Legacy Service", password = "SecretLegacyPassword")
        )
        val plaintextJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()),
            items
        ).toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintextJson, argonKey)

        val sBase64 = Base64.getEncoder().encodeToString(salt)
        val cBase64 = Base64.getEncoder().encodeToString(ciphertext)
        val chk = VaultExporter.computeSha256Checksum(cBase64)

        val hmacKey = VaultExporter.computeHmacSha256("kryptx-backup-integrity-key".toByteArray(Charsets.UTF_8), argonKey)
        val hmacBytes = VaultExporter.computeHmacSha256(ciphertext, hmacKey)
        val hmBase64 = Base64.getEncoder().encodeToString(hmacBytes)

        SecureMemory.wipe(hmacKey)
        SecureMemory.wipe(argonKey)
        SecureMemory.wipe(salt)
        SecureMemory.wipe(plaintextJson)
        SecureMemory.wipe(ciphertext)

        val header = BackupHeader(
            app = BackupHeader.CURRENT_APP_NAME,
            version = "2.4.0",
            formatVersion = 2,
            exportedAt = System.currentTimeMillis(),
            isEncrypted = true,
            kdfAlgorithm = "Argon2id",
            saltBase64 = sBase64,
            isPostQuantum = false,
            pqcEncapsulationBase64 = null,
            checksumSha256 = chk,
            hmacSha256Base64 = hmBase64
        )
        val payload = EncryptedBackupPayload(header, cBase64)

        dbHelper.savedItems.clear()
        val importResult = repository.importEncryptedBackup(payload, password)
        assertTrue("Classical backup must import cleanly", importResult is KryptxResult.Success)
        assertEquals(1, (importResult as KryptxResult.Success).data)
        assertEquals("Legacy Service", dbHelper.savedItems[0].title)
    }
}
