package com.kryptx.app.feature.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.designsystem.components.BottomNavTab
import com.kryptx.app.core.designsystem.components.FeatureGuide
import com.kryptx.app.core.designsystem.components.FeatureIntroSheet
import com.kryptx.app.core.designsystem.components.KryptxBottomNavBar
import com.kryptx.app.core.designsystem.theme.KryptxMotion
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
import com.kryptx.app.feature.settings.SecuritySettingsScreen
import com.kryptx.app.feature.settings.SettingsScreen
import com.kryptx.app.feature.settings.SettingsViewModel
import com.kryptx.app.feature.totp.TotpListScreen
import com.kryptx.app.feature.totp.TotpViewModel
import com.kryptx.app.feature.vault.AddEditItemScreen
import com.kryptx.app.feature.vault.VaultDashboardScreen
import com.kryptx.app.feature.vault.VaultItemDetailScreen
import com.kryptx.app.feature.vault.VaultViewModel

fun BottomNavTab.toScreen(): Screen = when (this) {
    BottomNavTab.VAULT -> Screen.VaultDashboard
    BottomNavTab.TOTP -> Screen.TotpList
    BottomNavTab.GENERATOR -> Screen.Generator
    BottomNavTab.SETTINGS -> Screen.Settings
}

fun parseScreenRoute(route: String): Screen = when {
    route == Screen.Onboarding.route           -> Screen.Onboarding
    route == Screen.SetupPermissions.route     -> Screen.SetupPermissions
    route == Screen.SetupMasterPassword.route  -> Screen.SetupMasterPassword
    route == Screen.Unlock.route               -> Screen.Unlock
    route == Screen.VaultDashboard.route       -> Screen.VaultDashboard
    route == Screen.TotpList.route             -> Screen.TotpList
    route == Screen.Generator.route            -> Screen.Generator
    route == Screen.SecurityCenter.route       -> Screen.SecurityCenter
    route == Screen.Settings.route             -> Screen.Settings
    route == Screen.Search.route               -> Screen.Search
    route == Screen.SecuritySettings.route     -> Screen.SecuritySettings
    route == Screen.AppearanceSettings.route   -> Screen.AppearanceSettings
    route == Screen.BackupExport.route         -> Screen.BackupExport
    route.startsWith(Screen.ItemDetail.ROUTE_PREFIX) -> {
        Screen.ItemDetail(route.removePrefix(Screen.ItemDetail.ROUTE_PREFIX))
    }
    route.startsWith(Screen.AddEditItem.ROUTE_PREFIX) -> {
        val id = route.removePrefix(Screen.AddEditItem.ROUTE_PREFIX)
        Screen.AddEditItem(id.ifBlank { null })
    }
    else -> Screen.VaultDashboard
}

private val ScreenBackStackSaver = Saver<SnapshotStateList<Screen>, List<String>>(
    save = { list ->
        // SECURITY: Only persist pre-auth screens. Post-auth deep navigation
        // state is intentionally NOT restored after process death — the user
        // must re-authenticate. This prevents stale screen state from leaking
        // vault context across process boundaries.
        list.filter {
            it is Screen.Onboarding || it is Screen.SetupPermissions ||
            it is Screen.SetupMasterPassword || it is Screen.Unlock
        }.map { it.route }
    },
    restore = { routeList ->
        routeList.map { parseScreenRoute(it) }.toMutableStateList()
    }
)

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
    vaultRepository: VaultRepository,
    onTriggerBiometrics: () -> Unit,
    modifier: Modifier = Modifier,
    pendingShortcutTarget: String? = null,
    onClearPendingShortcut: () -> Unit = {},
    onEnrollBiometrics: ((Boolean) -> Unit) -> Unit = {}
) {
    val isUnlocked by unlockViewModel.isUnlocked.collectAsState()
    val unlockUiState by unlockViewModel.uiState.collectAsState()

    var selectedBottomTab by remember { mutableStateOf(BottomNavTab.VAULT) }
    var activeIntroFeature by remember { mutableStateOf<FeatureGuide?>(null) }

    // Navigation back stack with process-death survival
    val backStack = rememberSaveable(saver = ScreenBackStackSaver) {
        val initialList = mutableStateListOf<Screen>()
        if (!unlockUiState.hasVault) {
            initialList.add(Screen.Onboarding)
        } else if (!isUnlocked) {
            initialList.add(Screen.Unlock)
        } else {
            initialList.add(Screen.VaultDashboard)
        }
        initialList
    }

    // Synchronize navigation whenever unlock or vault state changes
    // Note: we use androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot to
    // ensure clear() + add() execute as a single atomic Compose snapshot transaction,
    // preventing a transient empty-backstack recomposition between the two operations.
    androidx.compose.runtime.LaunchedEffect(isUnlocked, unlockUiState.hasVault) {
        if (!unlockUiState.hasVault) {
            if (backStack.isEmpty() || (backStack.last() != Screen.Onboarding && backStack.last() != Screen.SetupPermissions && backStack.last() != Screen.SetupMasterPassword)) {
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    backStack.clear()
                    backStack.add(Screen.Onboarding)
                }
            }
        } else if (isUnlocked) {
            if (backStack.isEmpty() || backStack.last() == Screen.Unlock || backStack.last() == Screen.SetupMasterPassword || backStack.last() == Screen.SetupPermissions || backStack.last() == Screen.Onboarding) {
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    backStack.clear()
                    backStack.add(Screen.VaultDashboard)
                    selectedBottomTab = BottomNavTab.VAULT
                }
            }
        } else {
            if (backStack.isEmpty() || backStack.last() != Screen.Unlock) {
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    backStack.clear()
                    backStack.add(Screen.Unlock)
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }

    // Handle incoming launcher shortcuts when unlocked
    androidx.compose.runtime.LaunchedEffect(isUnlocked, pendingShortcutTarget) {
        if (isUnlocked && pendingShortcutTarget != null) {
            when (pendingShortcutTarget) {
                "search" -> {
                    navigateTo(Screen.Search)
                    onClearPendingShortcut()
                }
                "add_item" -> {
                    navigateTo(Screen.AddEditItem(null))
                    onClearPendingShortcut()
                }
                "generator" -> {
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                        selectedBottomTab = BottomNavTab.GENERATOR
                        backStack.clear()
                        backStack.add(Screen.Generator)
                    }
                    onClearPendingShortcut()
                }
                "totp" -> {
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                        selectedBottomTab = BottomNavTab.TOTP
                        backStack.clear()
                        backStack.add(Screen.TotpList)
                    }
                    onClearPendingShortcut()
                }
            }
        }
    }

    // Keep sync with vault state
    val currentScreen = when {
        !unlockUiState.hasVault && (backStack.isEmpty() || (backStack.last() !is Screen.SetupMasterPassword && backStack.last() !is Screen.SetupPermissions)) -> Screen.Onboarding
        !unlockUiState.hasVault && backStack.last() is Screen.SetupPermissions -> Screen.SetupPermissions
        !unlockUiState.hasVault && backStack.last() is Screen.SetupMasterPassword -> Screen.SetupMasterPassword
        !isUnlocked -> Screen.Unlock
        backStack.isEmpty() -> Screen.VaultDashboard
        else -> backStack.last()
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
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                selectedBottomTab = BottomNavTab.VAULT
                backStack.clear()
                backStack.add(Screen.VaultDashboard)
            }
        }
    }

    val showBottomBar = isUnlocked && (
            currentScreen == Screen.VaultDashboard ||
                    currentScreen == Screen.TotpList ||
                    currentScreen == Screen.Generator ||
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
                            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                selectedBottomTab = tab
                                backStack.clear()
                                backStack.add(tab.toScreen())
                            }
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
                    val isUnlockEntrance = (initialState is Screen.Unlock || initialState is Screen.SetupMasterPassword || initialState is Screen.SetupPermissions) &&
                            targetState == Screen.VaultDashboard
                    val isTabSwitch = (initialState in listOf(Screen.VaultDashboard, Screen.TotpList, Screen.Generator, Screen.Settings)) &&
                            (targetState in listOf(Screen.VaultDashboard, Screen.TotpList, Screen.Generator, Screen.Settings))

                    when {
                        isUnlockEntrance -> KryptxMotion.vaultUnlockEntrance()
                        isTabSwitch -> KryptxMotion.tabCrossfade()
                        backStack.size > 1 && backStack.lastOrNull() == targetState -> KryptxMotion.forwardTransition()
                        else -> KryptxMotion.backwardTransition()
                    }
                },
                label = "navigation_transition"
            ) { screen ->
                when (screen) {
                    Screen.Onboarding -> {
                        OnboardingScreen(
                            onFinishOnboarding = {
                                navigateTo(Screen.SetupPermissions)
                            }
                        )
                    }

                    Screen.SetupPermissions -> {
                        com.kryptx.app.feature.onboarding.SetupPermissionsScreen(
                            onContinue = {
                                navigateTo(Screen.SetupMasterPassword)
                            },
                            onBack = {
                                navigateBack()
                            }
                        )
                    }

                    Screen.SetupMasterPassword -> {
                        SetupMasterPasswordScreen(
                            viewModel = unlockViewModel,
                            onVaultCreated = {
                                backStack.clear()
                                backStack.add(Screen.VaultDashboard)
                            },
                            onEnrollBiometrics = onEnrollBiometrics
                        )
                    }

                    Screen.Unlock -> {
                        UnlockScreen(
                            viewModel = unlockViewModel,
                            onUnlockSuccess = {
                                backStack.clear()
                                backStack.add(Screen.VaultDashboard)
                            },
                            onTriggerBiometrics = onTriggerBiometrics,
                            onEnrollBiometrics = onEnrollBiometrics
                        )
                    }

                    Screen.VaultDashboard -> {
                        VaultDashboardScreen(
                            viewModel = vaultViewModel,
                            onNavigateToItemDetail = { id -> navigateTo(Screen.ItemDetail(id)) },
                            onNavigateToEditItem = { id -> navigateTo(Screen.AddEditItem(id)) },
                            onNavigateToAddItem = { navigateTo(Screen.AddEditItem(null)) },
                            onNavigateToSecurityCenter = { navigateTo(Screen.SecurityCenter) },
                            onNavigateToSearch = { navigateTo(Screen.Search) },
                            onEnrollBiometrics = onEnrollBiometrics
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
                            onNavigateToFixItem = { id -> navigateTo(Screen.AddEditItem(id)) },
                            onNavigateBack = { navigateBack() }
                        )
                    }

                    Screen.Settings -> {
                        SettingsScreen(
                            onNavigateToSecurity = { navigateTo(Screen.SecuritySettings) },
                            onNavigateToAppearance = { navigateTo(Screen.AppearanceSettings) },
                            onNavigateToBackup = { navigateTo(Screen.BackupExport) },
                            onReplayGuides = {
                                activeIntroFeature = FeatureGuide.VAULT
                            },
                            vaultRepository = vaultRepository,
                            settingsViewModel = settingsViewModel
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
                            onNavigateBack = { navigateBack() },
                            onEnrollBiometrics = onEnrollBiometrics
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

