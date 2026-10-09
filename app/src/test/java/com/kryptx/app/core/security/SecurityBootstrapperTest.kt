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
}
