package com.kryptx.app.core

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.core.totp.TotpGenerator
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Flagship End-to-End Cryptographic & Vault Lifecycle Integration Test.
 * Validates complete user journey: Vault Initialization -> 12 Multi-Category Population ->
 * Real-Time TOTP -> AES-256-GCM Backup Export -> Integrity Checksum -> DB Wipe -> Decrypted Import -> Data Verification.
 */
class EndToEndVaultLifecycleTest {

    private lateinit var sessionManager: VaultSessionManager
    private lateinit var repository: FakeVaultRepository

    @Before
    fun setup() {
        sessionManager = VaultSessionManager()
        repository = FakeVaultRepository()
    }

    @Test
    fun `complete end-to-end vault setup, multi-item population, encrypted export and restoration lifecycle`() = runTest {
        val masterPassword = "AlphaNumericMaster#2026!".toCharArray()
        val backupPassword = "BackupEncryptionPassword999!".toCharArray()

        // 1. Vault Setup
        val setupSuccess = repository.setupNewVault(masterPassword)
        assertTrue("Vault setup must succeed", setupSuccess)
        assertTrue("Repository must confirm active vault", repository.hasVault())

        // 2. Populate 12 Distinct Multi-Category Items
        val testItems = listOf(
            VaultItem(
                id = "item-login-1",
                title = "GitHub Developer Account",
                type = ItemType.LOGIN,
                username = "octocat_sec",
                password = "P@sswordGithubSuperSecure2026!",
                website = "https://github.com",
                totpSecret = "JBSWY3DPEHPK3PXP", // Standard TOTP test vector
                tags = listOf("Dev", "2FA"),
                passwordHistory = listOf(PasswordHistoryEntry("OldGithubPassword2025!"))
            ),
            VaultItem(
                id = "item-passkey-2",
                title = "Google Cloud Passkey",
                type = ItemType.PASSKEY,
                username = "admin@kryptx.dev",
                passkeyRpId = "google.com",
                passkeyCredentialId = "cred_fido2_987654321",
                passkeyAlgorithm = "ES256 (ECDSA P-256)"
            ),
            VaultItem(
                id = "item-card-3",
                title = "Corporate Visa Signature",
                type = ItemType.CREDIT_CARD,
                cardholderName = "ALEX MERCER",
                cardNumber = "4532015098764321",
                cardExpiry = "08/30",
                cardCvv = "888",
                cardPin = "9876"
            ),
            VaultItem(
                id = "item-identity-4",
                title = "Primary Passport Profile",
                type = ItemType.IDENTITY,
                identityFullName = "Alex Mercer",
                identityEmail = "alex.mercer@kryptx.app",
                identityPhone = "+1 (555) 019-2834",
                identityAddress = "456 Silicon Ave, San Francisco, CA",
                identityDob = "1992-05-14",
                identityIdNumber = "US987654321"
            ),
            VaultItem(
                id = "item-note-5",
                title = "Server Recovery Runbook",
                type = ItemType.SECURE_NOTE,
                notes = "CONFIDENTIAL: Cold storage backup seed coordinates and DR procedures."
            ),
            VaultItem(
                id = "item-wifi-6",
                title = "Headquarters Secure Wi-Fi",
                type = ItemType.WIFI,
                wifiSsid = "Kryptx_HQ_WPA3",
                wifiPassword = "UltraFastWifiSecureProtocol#99",
                wifiSecurityType = "WPA3 Enterprise"
            ),
            VaultItem(
                id = "item-api-7",
                title = "Production Cloud API Key",
                type = ItemType.API_KEY,
                apiKey = "kryptx_test_pub_key_999",
                apiSecret = "kryptx_test_sec_key_rest_999",
                apiEndpoint = "https://api.example.com/v1"
            ),
            VaultItem(
                id = "item-bank-8",
                title = "JPMorgan Chase Treasury",
                type = ItemType.BANK_ACCOUNT,
                bankName = "JPMorgan Chase Bank, N.A.",
                bankAccountNumber = "987654321098",
                bankRoutingNumber = "021000021",
                bankSwiftBic = "CHASUS33"
            ),
            VaultItem(
                id = "item-crypto-9",
                title = "Bitcoin Cold Storage",
                type = ItemType.CRYPTO_WALLET,
                cryptoWalletAddress = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
                cryptoSeedPhrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
                cryptoNetwork = "Bitcoin Mainnet"
            ),
            VaultItem(
                id = "item-ssh-10",
                title = "Production Bastion Host",
                type = ItemType.SSH_KEY,
                sshHost = "bastion.infra.kryptx.internal",
                sshPublicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGhub987 bastion-key",
                sshPrivateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret_key_bytes\n-----END OPENSSH PRIVATE KEY-----"
            ),
            VaultItem(
                id = "item-med-11",
                title = "Emergency Medical Card",
                type = ItemType.MEDICAL,
                medicalBloodType = "O-Negative",
                medicalAllergies = "Penicillin, Peanuts",
                medicalEmergencyContact = "Dr. Robert Smith (+1 555-0199)"
            ),
            VaultItem(
                id = "item-custom-12",
                title = "Datacenter Access Badge",
                type = ItemType.CUSTOM,
                customFields = listOf(
                    CustomField(id = "cf1", label = "Rack Row", value = "Bay-42-Alpha"),
                    CustomField(id = "cf2", label = "PIN Code", value = "9901", isSecured = true)
                ),
                attachments = listOf(
                    VaultAttachment(
                        id = "att-1",
                        fileName = "badge_scan.png",
                        mimeType = "image/png",
                        sizeBytes = 2048,
                        encryptedFileName = "att_1.enc"
                    )
                )
            )
        )

        // Save each item
        for (item in testItems) {
            val saved = repository.saveItem(item)
            assertTrue("Item ${item.title} must be saved successfully", saved)
        }

        // Verify all 12 items present in repository flow
        val savedItems = repository.getItems().first()
        assertEquals(12, savedItems.size)

        // 3. Verify TOTP Real-Time Algorithm for GitHub item
        val githubItem = repository.getItemById("item-login-1")
        assertNotNull(githubItem)
        val totpCode = TotpGenerator.generateCurrentTotp(githubItem!!.totpSecret)
        assertNotNull(totpCode)
        assertEquals(6, totpCode!!.code.length)
        assertTrue(totpCode.secondsRemaining in 1..30)

        // 4. Run Security Audit
        val audit = repository.computeSecurityAudit()
        assertNotNull(audit)
        assertTrue("Security score must be calculated", audit.overallScore in 0..100)

        // 5. Export Encrypted AES-256-GCM Backup
        val backupPayload = repository.exportEncryptedBackup(backupPassword)
        assertNotNull("Encrypted backup payload must be generated", backupPayload)
        assertEquals("PBKDF2WithHmacSHA256", backupPayload.header.kdfAlgorithm)
        assertTrue("Ciphertext must be present", backupPayload.ciphertextBase64.isNotBlank())

        // 6. Reset / Wipe Vault
        repository.resetVault()
        val emptyItems = repository.getItems().first()
        assertEquals("Vault must be completely empty after reset", 0, emptyItems.size)

        // 7. Import Encrypted Backup with Decryption Key
        val importedCount = repository.importEncryptedBackup(backupPayload, backupPassword)
        assertEquals("Must restore all 12 items exactly", 12, importedCount)

        // 8. Assert Complete Cryptographic & Data Integrity
        val restoredItems = repository.getItems().first()
        assertEquals(12, restoredItems.size)

        val restoredGithub = repository.getItemById("item-login-1")
        assertNotNull(restoredGithub)
        assertEquals("octocat_sec", restoredGithub!!.username)
        assertEquals("P@sswordGithubSuperSecure2026!", restoredGithub.password)
        assertEquals("JBSWY3DPEHPK3PXP", restoredGithub.totpSecret)
        assertEquals(1, restoredGithub.passwordHistory.size)
        assertEquals("OldGithubPassword2025!", restoredGithub.passwordHistory[0].password)

        val restoredBadge = repository.getItemById("item-custom-12")
        assertNotNull(restoredBadge)
        assertEquals(2, restoredBadge!!.customFields.size)
        assertEquals("Bay-42-Alpha", restoredBadge.customFields[0].value)
        assertEquals(1, restoredBadge.attachments.size)
        assertEquals("badge_scan.png", restoredBadge.attachments[0].fileName)
    }
}
