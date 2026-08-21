package com.kryptx.app.feature.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.designsystem.components.FeatureGuide
import com.kryptx.app.core.designsystem.components.FeatureIntroSheet
import com.kryptx.app.feature.auth.SetupMasterPasswordScreen
import com.kryptx.app.feature.auth.UnlockScreen
import com.kryptx.app.feature.auth.UnlockViewModel
import com.kryptx.app.feature.generator.GeneratorScreen
import com.kryptx.app.feature.generator.GeneratorViewModel
import com.kryptx.app.feature.onboarding.OnboardingScreen
import com.kryptx.app.feature.search.SearchScreen
import com.kryptx.app.feature.search.SearchViewModel
import com.kryptx.app.feature.securitycenter.SecurityCenterScreen
import com.kryptx.app.feature.securitycenter.SecurityCenterViewModel
import com.kryptx.app.feature.settings.AppearanceSettingsScreen
import com.kryptx.app.feature.settings.BackupExportScreen
import com.kryptx.app.feature.settings.PrivacyCenterScreen
import com.kryptx.app.feature.settings.SecurityAuditScreen
import com.kryptx.app.feature.settings.SecuritySettingsScreen
import com.kryptx.app.feature.settings.SettingsScreen
import com.kryptx.app.feature.settings.SettingsViewModel
import com.kryptx.app.feature.totp.TotpListScreen
import com.kryptx.app.feature.totp.TotpViewModel
import com.kryptx.app.feature.vault.AddEditItemScreen
import com.kryptx.app.feature.vault.VaultDashboardScreen
import com.kryptx.app.feature.vault.VaultItemDetailScreen
import com.kryptx.app.feature.vault.VaultViewModel

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
    val featureGuide: FeatureGuide
) {
    VAULT("Vault", Icons.Default.Lock, Screen.VaultDashboard, FeatureGuide.VAULT),
    TOTP("2FA", Icons.Default.Key, Screen.TotpList, FeatureGuide.TOTP),
    GENERATOR("Generator", Icons.Default.AutoAwesome, Screen.Generator, FeatureGuide.GENERATOR),
    SECURITY("Security", Icons.Default.Security, Screen.SecurityCenter, FeatureGuide.SECURITY),
    SETTINGS("Settings", Icons.Default.Settings, Screen.Settings, FeatureGuide.SETTINGS)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun KryptxNavGraph(
    unlockViewModel: UnlockViewModel,
    vaultViewModel: VaultViewModel,
    generatorViewModel: GeneratorViewModel,
    securitycenterViewModel: SecurityCenterViewModel,
    totpViewModel: TotpViewModel,
    searchViewModel: SearchViewModel,
    settingsViewModel: SettingsViewModel,
    preferencesRepository: IPreferencesRepository,
    vaultRepository: com.kryptx.app.core.database.VaultRepository = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.kryptx.app.KryptxApplication).vaultRepository,
    onTriggerBiometrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnlocked by unlockViewModel.isUnlocked.collectAsState()
    val unlockUiState by unlockViewModel.uiState.collectAsState()

    var selectedBottomTab by remember { mutableStateOf(BottomNavTab.VAULT) }
    var activeIntroFeature by remember { mutableStateOf<FeatureGuide?>(null) }

    fun checkAndShowFeatureIntro(feature: FeatureGuide) {
        if (!preferencesRepository.hasSeenFeatureIntro(feature.key)) {
            activeIntroFeature = feature
        }
    }

    // Trigger initial Vault intro when unlocked for the first time
    androidx.compose.runtime.LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            checkAndShowFeatureIntro(BottomNavTab.VAULT.featureGuide)
        }
    }

    // Navigation back stack
    val backStack = remember {
        mutableStateListOf<Screen>().apply {
            if (!unlockUiState.hasVault) {
                add(Screen.Onboarding)
            } else if (!isUnlocked) {
                add(Screen.Unlock)
            } else {
                add(Screen.VaultDashboard)
            }
        }
    }

    // Synchronize navigation whenever unlock or vault state changes
    androidx.compose.runtime.LaunchedEffect(isUnlocked, unlockUiState.hasVault) {
        if (!unlockUiState.hasVault) {
            if (backStack.isEmpty() || (backStack.last() != Screen.Onboarding && backStack.last() != Screen.SetupMasterPassword)) {
                backStack.clear()
                backStack.add(Screen.Onboarding)
            }
        } else if (isUnlocked) {
            if (backStack.isEmpty() || backStack.last() == Screen.Unlock || backStack.last() == Screen.SetupMasterPassword || backStack.last() == Screen.Onboarding) {
                backStack.clear()
                backStack.add(Screen.VaultDashboard)
                selectedBottomTab = BottomNavTab.VAULT
            }
        } else {
            if (backStack.isEmpty() || backStack.last() != Screen.Unlock) {
                backStack.clear()
                backStack.add(Screen.Unlock)
            }
        }
    }

    // Keep sync with vault state
    val currentScreen = when {
        !unlockUiState.hasVault && (backStack.isEmpty() || backStack.last() !is Screen.SetupMasterPassword) -> Screen.Onboarding
        !unlockUiState.hasVault && backStack.last() is Screen.SetupMasterPassword -> Screen.SetupMasterPassword
        !isUnlocked -> Screen.Unlock
        backStack.isEmpty() -> Screen.VaultDashboard
        else -> backStack.last()
    }

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    // Android System Back Button Handler
    val canHandleBack = (backStack.size > 1) || (isUnlocked && selectedBottomTab != BottomNavTab.VAULT)
    BackHandler(enabled = canHandleBack) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        } else if (isUnlocked && selectedBottomTab != BottomNavTab.VAULT) {
            selectedBottomTab = BottomNavTab.VAULT
            backStack.clear()
            backStack.add(Screen.VaultDashboard)
        }
    }

    val showBottomBar = isUnlocked && (
            currentScreen == Screen.VaultDashboard ||
                    currentScreen == Screen.TotpList ||
                    currentScreen == Screen.Generator ||
                    currentScreen == Screen.SecurityCenter ||
                    currentScreen == Screen.Settings
            )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                KryptxBottomNavBar(
                    selectedTab = selectedBottomTab,
                    onTabSelected = { tab ->
                        if (selectedBottomTab != tab) {
                            selectedBottomTab = tab
                            backStack.clear()
                            backStack.add(tab.screen)
                            checkAndShowFeatureIntro(tab.featureGuide)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) paddingValues.calculateBottomPadding() else 0.dp)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 4 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut())
                },
                label = "navigation_transition"
            ) { screen ->
                when (screen) {
                    Screen.Onboarding -> {
                        OnboardingScreen(
                            onFinishOnboarding = {
                                navigateTo(Screen.SetupMasterPassword)
                            }
                        )
                    }

                    Screen.SetupMasterPassword -> {
                        SetupMasterPasswordScreen(
                            viewModel = unlockViewModel,
                            onVaultCreated = {
                                backStack.clear()
                                backStack.add(Screen.VaultDashboard)
                            }
                        )
                    }

                    Screen.Unlock -> {
                        UnlockScreen(
                            viewModel = unlockViewModel,
                            onUnlockSuccess = {
                                backStack.clear()
                                backStack.add(Screen.VaultDashboard)
                            },
                            onTriggerBiometrics = onTriggerBiometrics
                        )
                    }

                    Screen.VaultDashboard -> {
                        VaultDashboardScreen(
                            viewModel = vaultViewModel,
                            onNavigateToItemDetail = { id -> navigateTo(Screen.ItemDetail(id)) },
                            onNavigateToAddItem = { navigateTo(Screen.AddEditItem(null)) },
                            onNavigateToSecurityCenter = {
                                selectedBottomTab = BottomNavTab.SECURITY
                                backStack.clear()
                                backStack.add(Screen.SecurityCenter)
                            },
                            onNavigateToSearch = { navigateTo(Screen.Search) }
                        )
                    }

                    Screen.TotpList -> {
                        TotpListScreen(viewModel = totpViewModel)
                    }

                    Screen.Generator -> {
                        GeneratorScreen(viewModel = generatorViewModel)
                    }

                    Screen.SecurityCenter -> {
                        SecurityCenterScreen(
                            viewModel = securitycenterViewModel,
                            onNavigateToFixItem = { id -> navigateTo(Screen.AddEditItem(id)) }
                        )
                    }

                    Screen.Settings -> {
                        SettingsScreen(
                            onNavigateToSecurity = { navigateTo(Screen.SecuritySettings) },
                            onNavigateToAppearance = { navigateTo(Screen.AppearanceSettings) },
                            onNavigateToBackup = { navigateTo(Screen.BackupExport) },
                            onNavigateToLocalSync = { navigateTo(Screen.LocalSync) },
                            onNavigateToPrivacy = { navigateTo(Screen.PrivacyCenter) },
                            onNavigateToAudit = { navigateTo(Screen.SecurityAudit) },
                            onNavigateToAutofillSetup = { navigateTo(Screen.SecuritySettings) },
                            onReplayGuides = {
                                preferencesRepository.resetAllFeatureIntros()
                                activeIntroFeature = FeatureGuide.VAULT
                            }
                        )
                    }

                    Screen.Search -> {
                        SearchScreen(
                            viewModel = searchViewModel,
                            onNavigateToItemDetail = { id -> navigateTo(Screen.ItemDetail(id)) },
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    is Screen.ItemDetail -> {
                        VaultItemDetailScreen(
                            itemId = screen.itemId,
                            viewModel = vaultViewModel,
                            onNavigateBack = { navigateBack() },
                            onNavigateToEdit = { id -> navigateTo(Screen.AddEditItem(id)) }
                        )
                    }

                    is Screen.AddEditItem -> {
                        AddEditItemScreen(
                            itemId = screen.itemId,
                            viewModel = vaultViewModel,
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.SecuritySettings -> {
                        SecuritySettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.AppearanceSettings -> {
                        AppearanceSettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.BackupExport -> {
                        BackupExportScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.LocalSync -> {
                        com.kryptx.app.feature.settings.LocalSyncScreen(
                            vaultRepository = vaultRepository,
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.PrivacyCenter -> {
                        PrivacyCenterScreen(
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.SecurityAudit -> {
                        SecurityAuditScreen(
                            onNavigateBack = { navigateBack() }
                        )
                    }
                }
            }
        }
    }

    activeIntroFeature?.let { feature ->
        FeatureIntroSheet(
            feature = feature,
            onDismiss = {
                preferencesRepository.markFeatureIntroSeen(feature.key)
                activeIntroFeature = null
            }
        )
    }
}

@Composable
fun KryptxBottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navBg = if (isDark) Color(0xFF0A0F1A).copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val navBorder = if (isDark) com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    val selectedPillBg = if (isDark) Color.White else com.kryptx.app.core.designsystem.theme.KryptxBlue
    val selectedIconTint = if (isDark) Color(0xFF070A12) else Color.White
    val unselectedIconTint = if (isDark) Color(0xFF8E9CAE) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(navBg)
                .border(1.dp, navBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab

                Box(
                    modifier = Modifier
                        .size(if (isSelected) 46.dp else 40.dp)
                        .clip(RoundedCornerShape(if (isSelected) 18.dp else 12.dp))
                        .background(if (isSelected) selectedPillBg else Color.Transparent)
                        .clickable {
                            com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                            onTabSelected(tab)
                        }
                        .semantics {
                            role = androidx.compose.ui.semantics.Role.Tab
                            contentDescription = "${tab.label} tab"
                            selected = isSelected
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) selectedIconTint else unselectedIconTint,
                        modifier = Modifier.size(if (isSelected) 22.dp else 20.dp)
                    )
                }
            }
        }
    }
}

