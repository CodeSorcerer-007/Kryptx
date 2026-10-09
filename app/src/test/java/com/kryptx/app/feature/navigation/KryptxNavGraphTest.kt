package com.kryptx.app.feature.navigation

import com.kryptx.app.core.designsystem.components.BottomNavTab
import org.junit.Assert.assertEquals
import org.junit.Test

class KryptxNavGraphTest {

    @Test
    fun parseScreenRoute_roundTripsAllScreenTypes() {
        val screens = listOf(
            Screen.Onboarding,
            Screen.SetupPermissions,
            Screen.SetupMasterPassword,
            Screen.Unlock,
            Screen.VaultDashboard,
            Screen.TotpList,
            Screen.Generator,
            Screen.SecurityCenter,
            Screen.Settings,
            Screen.Search,
            Screen.ItemDetail("test-uuid-123"),
            Screen.AddEditItem("edit-uuid-456"),
            Screen.AddEditItem(null),
            Screen.SecuritySettings,
            Screen.AppearanceSettings,
            Screen.BackupExport
        )

        screens.forEach { screen ->
            val parsed = parseScreenRoute(screen.route)
            assertEquals("Failed to parse route for $screen", screen, parsed)
        }
    }

    @Test
    fun parseScreenRoute_unknownRouteReturnsDashboard() {
        // Unknown future routes must fall back gracefully to VaultDashboard
        assertEquals(Screen.VaultDashboard, parseScreenRoute("unknown_future_route_v99"))
        assertEquals(Screen.VaultDashboard, parseScreenRoute(""))
    }

    @Test
    fun bottomNavTab_toScreen_mapsAllTabs() {
        assertEquals(Screen.VaultDashboard, BottomNavTab.VAULT.toScreen())
        assertEquals(Screen.TotpList, BottomNavTab.TOTP.toScreen())
        assertEquals(Screen.Generator, BottomNavTab.GENERATOR.toScreen())
        assertEquals(Screen.Settings, BottomNavTab.SETTINGS.toScreen())
    }
}
