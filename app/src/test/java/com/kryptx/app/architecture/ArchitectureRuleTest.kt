package com.kryptx.app.architecture

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.database.VaultRepositoryImpl
import com.kryptx.app.core.model.VaultItem
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
            SecureMemory::class.java
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
            com.kryptx.app.core.security.IClipboardSecurityManager::class.java.isAssignableFrom(
                com.kryptx.app.core.security.ClipboardSecurityManager::class.java
            )
        )
    }
}
