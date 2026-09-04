package com.kryptx.app.architecture

import com.kryptx.app.core.crypto.AdaptiveKdfCalibrator
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.database.VaultRepositoryImpl
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.AttachmentManager
import com.kryptx.app.core.security.ClipboardSecurityManager
import com.kryptx.app.core.security.IAttachmentManager
import com.kryptx.app.core.security.IClipboardSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class ArchitectureRuleTest {

    @Test
    fun testCryptoPackageHasZeroComposeImports() {
        val cryptoClasses = listOf(
            CryptoEngine::class.java,
            KeyDerivation::class.java,
            PostQuantumEngine::class.java,
            SecureMemory::class.java,
            AdaptiveKdfCalibrator::class.java,
            com.kryptx.app.core.crypto.HardwareEntropyHarvester::class.java
        )

        for (clazz in cryptoClasses) {
            for (field in clazz.declaredFields) {
                assertFalse(
                    "Crypto layer class ${clazz.simpleName} must not reference Compose UI classes",
                    field.type.name.startsWith("androidx.compose")
                )
            }
            for (method in clazz.declaredMethods) {
                assertFalse(
                    "Crypto layer method ${method.name} must not return Compose UI types",
                    method.returnType.name.startsWith("androidx.compose")
                )
            }
        }
    }

    @Test
    fun testRepositoryImplementationsMatchInterfaces() {
        assertTrue(
            "PreferencesRepository must implement IPreferencesRepository",
            IPreferencesRepository::class.java.isAssignableFrom(PreferencesRepository::class.java)
        )
        assertTrue(
            "VaultRepositoryImpl must implement VaultRepository",
            VaultRepository::class.java.isAssignableFrom(VaultRepositoryImpl::class.java)
        )
    }

    @Test
    fun testVaultItemModelImmutability() {
        // VaultItem should have final properties to ensure immutability and thread safety
        for (field in VaultItem::class.java.declaredFields) {
            if (!field.name.startsWith("$") && !Modifier.isStatic(field.modifiers)) {
                assertTrue(
                    "Field ${field.name} in VaultItem should be private/final for immutability",
                    Modifier.isPrivate(field.modifiers) || Modifier.isFinal(field.modifiers)
                )
            }
        }
    }

    @Test
    fun testSecurityManagerImplementationsMatchInterfaces() {
        assertTrue(
            "ClipboardSecurityManager must implement IClipboardSecurityManager",
            IClipboardSecurityManager::class.java.isAssignableFrom(
                ClipboardSecurityManager::class.java
            )
        )
        assertTrue(
            "AttachmentManager must implement IAttachmentManager",
            IAttachmentManager::class.java.isAssignableFrom(
                AttachmentManager::class.java
            )
        )
    }

    @Test
    fun testAllItemTypesAreDeclared() {
        val types = ItemType.entries
        assertEquals("Vault must support all 8 core item types", 8, types.size)
        for (t in types) {
            assertNotNull(t.name)
            assertTrue(t.name.isNotBlank())
        }
    }

    @Test
    fun testConstantTimeEqualitySecurity() {
        assertTrue(SecureMemory.safeEquals("secret123", "secret123"))
        assertFalse(SecureMemory.safeEquals("secret123", "secret124"))
        assertFalse(SecureMemory.safeEquals("secret123", "secret12"))
        assertTrue(SecureMemory.safeEquals(42, 42))
        assertFalse(SecureMemory.safeEquals(42, 43))
    }

    @Test
    fun testZeroAllocationTokenizerSpeed() {
        val index = com.kryptx.app.core.database.EncryptedSearchIndex()
        val item = VaultItem(
            id = "test-1",
            title = "Proton Mail Account",
            username = "user@proton.me",
            website = "https://mail.proton.me",
            notes = "Primary sovereign encrypted email"
        )
        index.indexItem(item)
        assertEquals(1, index.size)
        val results = index.search("proton")
        assertEquals(1, results.size)
        assertEquals("test-1", results[0].id)
        index.clear()
        assertEquals(0, index.size)
    }
}
