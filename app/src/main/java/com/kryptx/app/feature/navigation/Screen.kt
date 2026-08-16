package com.kryptx.app.feature.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object SetupMasterPassword : Screen("setup_master_password")
    data object Unlock : Screen("unlock")

    // Main bottom nav tabs
    data object VaultDashboard : Screen("vault_dashboard")
    data object TotpList : Screen("totp_list")
    data object Generator : Screen("generator")
    data object SecurityCenter : Screen("security_center")
    data object Settings : Screen("settings")

    // Secondary screens
    data object Search : Screen("search")
    data class ItemDetail(val itemId: String) : Screen("item_detail/$itemId") {
        companion object {
            const val ROUTE = "item_detail/{itemId}"
        }
    }
    data class AddEditItem(val itemId: String?) : Screen("add_edit_item?itemId=${itemId ?: ""}") {
        companion object {
            const val ROUTE = "add_edit_item?itemId={itemId}"
        }
    }

    data object SecuritySettings : Screen("security_settings")
    data object AppearanceSettings : Screen("appearance_settings")
    data object BackupExport : Screen("backup_export")
    data object LocalSync : Screen("local_sync")
    data object PrivacyCenter : Screen("privacy_center")
    data object SecurityAudit : Screen("security_audit")
}
