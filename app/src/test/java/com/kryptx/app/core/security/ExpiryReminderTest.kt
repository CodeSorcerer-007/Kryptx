package com.kryptx.app.core.security

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryReminderTest {

    @Test
    fun `test item expiration detection`() {
        val now = System.currentTimeMillis()
        val expiredItem = VaultItem(
            title = "Corporate VPN",
            type = ItemType.LOGIN,
            password = "SecretPassword123!",
            expiresAt = now - 1000L, // 1 second ago
            rotationIntervalDays = 90
        )

        val activeItem = VaultItem(
            title = "Personal Email",
            type = ItemType.LOGIN,
            password = "SecretPassword123!",
            expiresAt = now + (30L * 24 * 60 * 60 * 1000L), // 30 days in future
            rotationIntervalDays = 60
        )

        val noExpiryItem = VaultItem(
            title = "Wi-Fi",
            type = ItemType.WIFI,
            wifiPassword = "Password"
        )

        assertTrue(expiredItem.isExpired)
        assertFalse(activeItem.isExpired)
        assertFalse(noExpiryItem.isExpired)

        val daysLeft = activeItem.daysUntilExpiration
        assertEquals(30L, daysLeft)
    }
}
