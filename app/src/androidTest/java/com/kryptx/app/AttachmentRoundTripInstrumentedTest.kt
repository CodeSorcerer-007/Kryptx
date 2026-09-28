package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.core.security.AttachmentManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class AttachmentRoundTripInstrumentedTest {

    private lateinit var attachmentManager: AttachmentManager
    private lateinit var sessionManager: VaultSessionManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        sessionManager = VaultSessionManager()
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        sessionManager.unlock(key)
        attachmentManager = AttachmentManager(context, sessionManager)
    }

    @Test
    fun testAttachmentEncryptionAndDecryptionRoundTrip() = runBlocking {
        val sampleData = "Secure Attachment Payload with Multi-Chunk Data. ".repeat(500).toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(sampleData)

        val attachment = attachmentManager.saveAttachmentStream(
            fileName = "secure_document.pdf",
            mimeType = "application/pdf",
            inputStream = stream
        )

        assertNotNull("Attachment metadata should be generated", attachment)
        assertTrue("Attachment size must match plaintext size", attachment!!.sizeBytes == sampleData.size.toLong())

        val decryptedBytes = attachmentManager.loadDecryptedAttachment(attachment)
        assertNotNull("Decrypted bytes should not be null", decryptedBytes)
        assertArrayEquals("Decrypted attachment content must match original payload", sampleData, decryptedBytes)

        val deleted = attachmentManager.deleteAttachment(attachment)
        assertTrue("Attachment deletion should succeed", deleted)
    }
}
