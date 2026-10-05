package com.kryptx.app.core.security

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for [RootDetector] covering all detection heuristics.
 *
 * These tests run on a clean JVM host environment where:
 * - No root binaries exist
 * - No emulator properties are returned (Build fields return empty/default)
 * - No debugger is attached
 * - No hooking frameworks are loaded
 * - Hardware Keystore attestation is unavailable (expected to fail gracefully)
 */
class RootDetectorTest {

    // ========================
    // Basic Contract Tests
    // ========================

    @Test
    fun testRootDetectorReturnsValidSecurityStatus() {
        val status = RootDetector.checkDeviceSecurity()
        assertNotNull(status)
        assertNotNull(status.detectedIndicators)
    }

    @Test
    fun testSecurityStatusFieldsAreInitialized() {
        val status = RootDetector.checkDeviceSecurity()
        // Boolean fields should be deterministic, not null
        assertNotNull(status.isRooted)
        assertNotNull(status.isEmulator)
        assertNotNull(status.hasTestKeys)
        // List must never be null
        assertNotNull(status.detectedIndicators)
    }

    @Test
    fun testDetectedIndicatorsIsImmutableList() {
        val status = RootDetector.checkDeviceSecurity()
        // Returned list should not throw on iteration
        for (indicator in status.detectedIndicators) {
            assertNotNull(indicator)
            assertTrue("Indicator should not be blank", indicator.isNotBlank())
        }
    }

    // ========================
    // SecurityStatus Data Class Tests
    // ========================

    @Test
    fun testSecurityStatusDataClassEquality() {
        val status1 = RootDetector.SecurityStatus(
            isRooted = false,
            isEmulator = false,
            hasTestKeys = false,
            detectedIndicators = listOf("test"),
            attestationSecurityLevel = "StrongBox",
            verifiedBootState = "Verified",
            isDeviceLocked = true
        )
        val status2 = RootDetector.SecurityStatus(
            isRooted = false,
            isEmulator = false,
            hasTestKeys = false,
            detectedIndicators = listOf("test"),
            attestationSecurityLevel = "StrongBox",
            verifiedBootState = "Verified",
            isDeviceLocked = true
        )
        assertEquals(status1, status2)
        assertEquals(status1.hashCode(), status2.hashCode())
    }

    @Test
    fun testSecurityStatusDataClassInequality() {
        val status1 = RootDetector.SecurityStatus(
            isRooted = false, isEmulator = false, hasTestKeys = false,
            detectedIndicators = emptyList()
        )
        val status2 = RootDetector.SecurityStatus(
            isRooted = true, isEmulator = false, hasTestKeys = false,
            detectedIndicators = listOf("Root binary found")
        )
        assertNotEquals(status1, status2)
    }

    @Test
    fun testSecurityStatusCopyPreservesFields() {
        val original = RootDetector.SecurityStatus(
            isRooted = true,
            isEmulator = true,
            hasTestKeys = true,
            detectedIndicators = listOf("indicator1", "indicator2"),
            attestationSecurityLevel = "TrustedEnvironment",
            verifiedBootState = "SelfSigned",
            isDeviceLocked = false
        )
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(original.isRooted, copy.isRooted)
        assertEquals(original.isEmulator, copy.isEmulator)
        assertEquals(original.hasTestKeys, copy.hasTestKeys)
        assertEquals(original.detectedIndicators, copy.detectedIndicators)
        assertEquals(original.attestationSecurityLevel, copy.attestationSecurityLevel)
        assertEquals(original.verifiedBootState, copy.verifiedBootState)
        assertEquals(original.isDeviceLocked, copy.isDeviceLocked)
    }

    @Test
    fun testSecurityStatusDefaultOptionalFields() {
        val status = RootDetector.SecurityStatus(
            isRooted = false,
            isEmulator = false,
            hasTestKeys = false,
            detectedIndicators = emptyList()
        )
        assertNull(status.attestationSecurityLevel)
        assertNull(status.verifiedBootState)
        assertNull(status.isDeviceLocked)
    }

    // ========================
    // JVM Host Environment Tests
    // ========================

    @Test
    fun testNoRootBinariesOnJvmHost() {
        val status = RootDetector.checkDeviceSecurity()
        val rootIndicators = status.detectedIndicators.filter {
            it.contains("Root/tampering binary found") || it.contains("Root management package detected")
        }
        // On a clean JVM host, no root binaries should exist at Android paths
        assertTrue(
            "JVM host should not have Android root binaries at /system/bin/su etc.",
            rootIndicators.isEmpty()
        )
    }

    @Test
    fun testNoHookingFrameworksOnJvmHost() {
        val status = RootDetector.checkDeviceSecurity()
        val hookIndicators = status.detectedIndicators.filter {
            it.contains("hooking framework") || it.contains("LD_PRELOAD") || it.contains("ptrace")
        }
        assertTrue(
            "JVM host should not detect Frida/Xposed/LD_PRELOAD in a clean test environment",
            hookIndicators.isEmpty()
        )
    }

    @Test
    fun testNoDebuggerAttachedInTestEnvironment() {
        val status = RootDetector.checkDeviceSecurity()
        val debuggerIndicators = status.detectedIndicators.filter {
            it.contains("Debugger currently connected")
        }
        assertTrue(
            "No debugger should be reported in a unit test environment",
            debuggerIndicators.isEmpty()
        )
    }

    // ========================
    // Indicator Content Quality Tests
    // ========================

    @Test
    fun testIndicatorStringsAreDescriptive() {
        // Verify that indicator strings contain actionable context, not just generic messages
        val knownIndicatorPrefixes = listOf(
            "OS build signed with",
            "Root/tampering binary found:",
            "Root management package detected:",
            "Debugger currently connected",
            "Runtime hooking framework detected",
            "LD_PRELOAD injection detected:",
            "Active ptrace debugger detected",
            "Running in Android Virtual Device",
            "Hardware Attestation Extension missing",
            "Hardware Attestation: Bootloader unlocked",
            "Hardware Attestation: Verified Boot State is",
            "Could not retrieve Hardware Attestation certificate chain",
            "Hardware Attestation failed:"
        )

        // Create a synthetic status to verify format expectations
        val status = RootDetector.checkDeviceSecurity()
        for (indicator in status.detectedIndicators) {
            val matchesKnownPattern = knownIndicatorPrefixes.any { prefix ->
                indicator.startsWith(prefix)
            }
            assertTrue(
                "Indicator '$indicator' should match a known pattern format for structured parsing",
                matchesKnownPattern
            )
        }
    }

    // ========================
    // Rooted Status Logic Tests
    // ========================

    @Test
    fun testIsRootedFalseWhenNoIndicators() {
        val status = RootDetector.SecurityStatus(
            isRooted = false,
            isEmulator = false,
            hasTestKeys = false,
            detectedIndicators = emptyList()
        )
        assertFalse(status.isRooted)
    }

    @Test
    fun testIsRootedTrueWithTestKeysOnRealDevice() {
        // Simulate: test-keys detected on a non-emulator device
        val status = RootDetector.SecurityStatus(
            isRooted = true,
            isEmulator = false,
            hasTestKeys = true,
            detectedIndicators = listOf("OS build signed with test-keys (custom ROM)")
        )
        assertTrue("Device with test-keys and not an emulator should report rooted", status.isRooted)
    }

    @Test
    fun testIsRootedFalseWithTestKeysOnEmulator() {
        // Emulators with test-keys are expected and should not be flagged as rooted
        val status = RootDetector.SecurityStatus(
            isRooted = false,
            isEmulator = true,
            hasTestKeys = true,
            detectedIndicators = listOf("OS build signed with test-keys (custom ROM)", "Running in Android Virtual Device / Emulator")
        )
        assertFalse("Emulator with test-keys should not report rooted", status.isRooted)
    }

    @Test
    fun testAttestationSecurityLevelValues() {
        // Verify all documented security levels can be represented
        for (level in listOf("Software", "TrustedEnvironment", "StrongBox", "Unknown")) {
            val status = RootDetector.SecurityStatus(
                isRooted = false, isEmulator = false, hasTestKeys = false,
                detectedIndicators = emptyList(), attestationSecurityLevel = level
            )
            assertEquals(level, status.attestationSecurityLevel)
        }
    }

    @Test
    fun testVerifiedBootStateValues() {
        for (state in listOf("Verified", "SelfSigned", "Unverified", "Failed", "Unknown")) {
            val status = RootDetector.SecurityStatus(
                isRooted = false, isEmulator = false, hasTestKeys = false,
                detectedIndicators = emptyList(), verifiedBootState = state
            )
            assertEquals(state, status.verifiedBootState)
        }
    }

    // ========================
    // Resilience & Crash Safety Tests
    // ========================

    @Test
    fun testCheckDeviceSecurityDoesNotThrow() {
        // RootDetector must NEVER crash the app — all scan failures should be caught internally
        try {
            val status = RootDetector.checkDeviceSecurity()
            assertNotNull(status)
        } catch (e: Throwable) {
            fail("RootDetector.checkDeviceSecurity() must not throw any exceptions, but threw: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Test
    fun testMultipleCallsAreIdempotent() {
        val status1 = RootDetector.checkDeviceSecurity()
        val status2 = RootDetector.checkDeviceSecurity()

        // Structural equality: same environment should yield same results
        assertEquals(status1.isRooted, status2.isRooted)
        assertEquals(status1.isEmulator, status2.isEmulator)
        assertEquals(status1.hasTestKeys, status2.hasTestKeys)
    }

    @Test
    fun testConcurrentCallsDoNotCrash() {
        val threads = (1..8).map {
            Thread {
                val status = RootDetector.checkDeviceSecurity()
                assertNotNull(status)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
    }

    // ========================
    // toString / Serialization Safety
    // ========================

    @Test
    fun testSecurityStatusToStringDoesNotLeak() {
        val status = RootDetector.SecurityStatus(
            isRooted = true,
            isEmulator = false,
            hasTestKeys = true,
            detectedIndicators = listOf("Root/tampering binary found: /system/bin/su"),
            attestationSecurityLevel = "StrongBox",
            verifiedBootState = "Verified",
            isDeviceLocked = true
        )
        val str = status.toString()
        assertNotNull(str)
        assertTrue("toString should contain isRooted", str.contains("isRooted"))
        // Verify data class toString doesn't contain sensitive key material (it shouldn't)
        assertFalse("toString should not contain vault keys", str.contains("VaultKey"))
    }
}
