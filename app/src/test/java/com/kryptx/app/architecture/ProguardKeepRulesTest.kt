package com.kryptx.app.architecture

import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.SecurityIssue
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.Serializable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardKeepRulesTest {

    @Test
    fun testProguardRulesFileExistsAndHasCriticalKeepRules() {
        val candidates = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro"),
            File("../app/proguard-rules.pro")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("proguard-rules.pro must exist in project root or app module", file)

        val content = file!!.readText()

        assertTrue("Must keep kotlinx.serialization companion and fields",
            content.contains("kotlinx.serialization") && content.contains("<fields>"))

        assertTrue("Must keep UniFFI native Rust bindings",
            content.contains("uniffi.**") || content.contains("com.kryptx.app.core.crypto.generated"))

        assertTrue("Must keep SQLCipher database classes",
            content.contains("net.zetetic.database"))

        assertTrue("Must keep AutofillService class",
            content.contains("AutofillService"))

        assertTrue("Must strip Log.* in production builds",
            content.contains("assumenosideeffects class android.util.Log"))
    }

    @Test
    fun testAllPersistentDataModelsHaveSerializableAnnotation() {
        val modelClasses = listOf(
            VaultItem::class.java,
            BackupHeader::class.java,
            EncryptedBackupPayload::class.java,
            CustomField::class.java,
            PasswordHistoryEntry::class.java,
            VaultAttachment::class.java,
            SecurityAuditReport::class.java,
            SecurityIssue::class.java
        )

        for (clazz in modelClasses) {
            val isSerializable = clazz.isAnnotationPresent(Serializable::class.java)
            assertTrue(
                "Model class ${clazz.simpleName} must have @Serializable annotation for R8/ProGuard preservation",
                isSerializable
            )
        }
    }
}
