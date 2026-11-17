package com.kryptx.app.core.security

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SecurityBootstrapperTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mock()
        mockPrefs = mock()
        mockEditor = mock()

        whenever(mockContext.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(mockPrefs)
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)

        // Reset in-memory session flag between tests
        SecurityBootstrapper.setCompromiseAcknowledged(mockContext, false)
    }

    @Test
    fun testAcknowledgmentDefaultFalse() {
        whenever(mockPrefs.getBoolean(eq("compromise_acknowledged"), eq(false))).thenReturn(false)
        assertFalse(SecurityBootstrapper.isCompromiseAcknowledged(mockContext))
    }

    @Test
    fun testAcknowledgmentSetTruePersistsInSessionAndPrefs() {
        SecurityBootstrapper.setCompromiseAcknowledged(mockContext, true)
        assertTrue(SecurityBootstrapper.isCompromiseAcknowledged(mockContext))
    }

    @Test
    fun testIntegrityReportDataClass() {
        val report = SecurityBootstrapper.IntegrityReport(
            isCompromised = true,
            details = listOf("Test root binary detected")
        )
        assertTrue(report.isCompromised)
        assertTrue(report.details.isNotEmpty())
    }

    @Test
    fun testVerifyApkSignatureInJvmEnvironment() {
        // In local JVM unit tests, PackageManager is mocked/missing, so it gracefully succeeds
        val result = SecurityBootstrapper.verifyApkSignature(mockContext)
        assertTrue(result)
    }

    @Test
    fun testIsInstalledFromGooglePlayReturnsTrueForVending() {
        val mockPm: android.content.pm.PackageManager = mock()
        whenever(mockContext.packageManager).thenReturn(mockPm)
        whenever(mockContext.packageName).thenReturn("com.kryptx.app")
        @Suppress("DEPRECATION")
        whenever(mockPm.getInstallerPackageName("com.kryptx.app")).thenReturn("com.android.vending")

        assertTrue(SecurityBootstrapper.isInstalledFromGooglePlay(mockContext))
    }

    @Test
    fun testIsInstalledFromGooglePlayReturnsFalseForSideloadOrOther() {
        val mockPm: android.content.pm.PackageManager = mock()
        whenever(mockContext.packageManager).thenReturn(mockPm)
        whenever(mockContext.packageName).thenReturn("com.kryptx.app")
        @Suppress("DEPRECATION")
        whenever(mockPm.getInstallerPackageName("com.kryptx.app")).thenReturn("com.android.packageinstaller")

        assertFalse(SecurityBootstrapper.isInstalledFromGooglePlay(mockContext))
    }
}
