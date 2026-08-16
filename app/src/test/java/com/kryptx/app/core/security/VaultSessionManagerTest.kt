package com.kryptx.app.core.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

@OptIn(ExperimentalCoroutinesApi::class)
class VaultSessionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testUnlockSetsActiveKeyAndState() {
        val sessionManager = VaultSessionManager(testScope)
        val key = ByteArray(32) { it.toByte() }

        assertFalse(sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())

        sessionManager.unlock(key)

        assertTrue(sessionManager.isUnlocked.value)
        assertNotNull(sessionManager.getVaultKey())
        assertTrue(Arrays.equals(key, sessionManager.getVaultKey()))
        assertEquals(0, sessionManager.failedAttempts.value)
    }

    @Test
    fun testLockZeroizesKeyAndClearsState() {
        val sessionManager = VaultSessionManager(testScope)
        val key = ByteArray(32) { (it + 1).toByte() }

        sessionManager.unlock(key)
        assertTrue(sessionManager.isUnlocked.value)

        sessionManager.lock()

        assertFalse(sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun testAutoLockTimeoutTriggersLock() = runTest(testDispatcher) {
        val sessionManager = VaultSessionManager(this)
        val key = ByteArray(32) { 42 }

        sessionManager.setAutoLockTimeout(VaultSessionManager.AutoLockTimeout.THIRTY_SECONDS)
        sessionManager.unlock(key)

        assertTrue(sessionManager.isUnlocked.value)

        // Advance 29 seconds (should still be unlocked)
        advanceTimeBy(29_000)
        assertTrue(sessionManager.isUnlocked.value)

        // Advance past 30 seconds
        advanceTimeBy(2_000)
        assertFalse(sessionManager.isUnlocked.value)
        assertTrue(sessionManager.isLockedDueToTimeout.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun testBackgroundImmediateLock() {
        val sessionManager = VaultSessionManager(testScope)
        val key = ByteArray(32) { 7 }

        sessionManager.setLockOnBackground(true)
        sessionManager.unlock(key)
        assertTrue(sessionManager.isUnlocked.value)

        sessionManager.onAppBackgrounded()
        assertFalse(sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun testFailedAttemptsExponentialLockout() = runTest(testDispatcher) {
        val sessionManager = VaultSessionManager(this)

        assertEquals(0, sessionManager.failedAttempts.value)
        assertEquals(0, sessionManager.lockoutSecondsRemaining.value)

        sessionManager.recordFailedAttempt()
        assertEquals(1, sessionManager.failedAttempts.value)
        assertEquals(0, sessionManager.lockoutSecondsRemaining.value)

        sessionManager.recordFailedAttempt()
        assertEquals(2, sessionManager.failedAttempts.value)
        assertEquals(0, sessionManager.lockoutSecondsRemaining.value)

        // 3rd attempt triggers 10 second lockout
        sessionManager.recordFailedAttempt()
        assertEquals(3, sessionManager.failedAttempts.value)
        assertEquals(10, sessionManager.lockoutSecondsRemaining.value)

        // Advance 5 seconds
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertEquals(5, sessionManager.lockoutSecondsRemaining.value)

        // 5th attempt triggers 30 second lockout
        sessionManager.recordFailedAttempt()
        sessionManager.recordFailedAttempt()
        assertEquals(5, sessionManager.failedAttempts.value)
        assertEquals(30, sessionManager.lockoutSecondsRemaining.value)
    }

    @Test
    fun testWithVaultKeyExecutesSafely() {
        val sessionManager = VaultSessionManager(testScope)
        val key = ByteArray(32) { 99 }

        val resultLocked = sessionManager.withVaultKey { it.size }
        assertNull(resultLocked)

        sessionManager.unlock(key)
        val resultUnlocked = sessionManager.withVaultKey { it.size }
        assertEquals(32, resultUnlocked)
    }
}
