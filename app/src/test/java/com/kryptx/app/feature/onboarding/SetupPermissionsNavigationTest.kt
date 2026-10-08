package com.kryptx.app.feature.onboarding

import com.kryptx.app.feature.navigation.Screen
import com.kryptx.app.feature.navigation.parseScreenRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPermissionsNavigationTest {

    @Test
    fun testSetupPermissionsRouteParsing() {
        assertEquals(Screen.SetupPermissions, parseScreenRoute("setup_permissions"))
        assertEquals("setup_permissions", Screen.SetupPermissions.route)
        assertEquals(Screen.SetupPermissions, parseScreenRoute(Screen.SetupPermissions.route))
    }

    @Test
    fun testFirstTimeSetupRouteHierarchy() {
        val onboarding = Screen.Onboarding
        val permissions = Screen.SetupPermissions
        val masterPassword = Screen.SetupMasterPassword
        val dashboard = Screen.VaultDashboard

        assertEquals("onboarding", onboarding.route)
        assertEquals("setup_permissions", permissions.route)
        assertEquals("setup_master_password", masterPassword.route)
        assertEquals("vault_dashboard", dashboard.route)

        // Verify all setup routes resolve deterministically through parseScreenRoute
        val routes = listOf(onboarding, permissions, masterPassword, dashboard)
        routes.forEach { screen ->
            assertEquals(screen, parseScreenRoute(screen.route))
        }
    }
}
