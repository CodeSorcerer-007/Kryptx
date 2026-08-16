package com.kryptx.app.core.security

import org.junit.Assert.assertNotNull
import org.junit.Test

class RootDetectorTest {

    @Test
    fun testRootDetectorReturnsValidSecurityStatus() {
        val status = RootDetector.checkDeviceSecurity()
        assertNotNull(status)
        assertNotNull(status.detectedIndicators)
        // Ensure no unexpected exceptions or crashes occurred during scan
    }
}
