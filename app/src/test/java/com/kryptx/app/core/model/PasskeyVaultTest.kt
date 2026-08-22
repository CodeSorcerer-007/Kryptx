package com.kryptx.app.core.model

import com.kryptx.app.core.migration.VaultExporter
import com.kryptx.app.core.migration.VaultImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PasskeyVaultTest {

    @Test
    fun `Passkey item properly initializes and resolves domain and primary secret`() {
        val passkeyItem = VaultItem(
            id = UUID.randomUUID().toString(),
            title = "GitHub Passkey",
            type = ItemType.PASSKEY,
            username = "octocat@github.com",
            passkeyRpId = "github.com",
            passkeyCredentialId = "cred_base64_xyz123",
            passkeyUserHandle = "user_handle_abc",
            passkeyAlgorithm = "ES256"
        )

        assertEquals("github.com", passkeyItem.domain)
        assertEquals("github.com • octocat@github.com", passkeyItem.displaySubtitle)
        assertEquals("cred_base64_xyz123", passkeyItem.primarySecret)
    }

    @Test
    fun `Passkey item exports to CSV and re-imports accurately`() {
        val originalItem = VaultItem(
            id = UUID.randomUUID().toString(),
            title = "Google Passkey",
            type = ItemType.PASSKEY,
            username = "security@google.com",
            passkeyRpId = "google.com",
            passkeyCredentialId = "fido2_cred_id_999",
            passkeyAlgorithm = "ES256"
        )

        val csv = VaultExporter.exportToCsv(listOf(originalItem))
        val importedList = VaultImporter.importCsv(csv)

        assertEquals(1, importedList.size)
        val imported = importedList[0]
        assertEquals(ItemType.PASSKEY, imported.type)
        assertEquals("security@google.com", imported.username)
        assertEquals("google.com", imported.passkeyRpId)
        assertEquals("fido2_cred_id_999", imported.passkeyCredentialId)
    }
}
