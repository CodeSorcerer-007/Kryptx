package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.FolderStatPill
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxFolderCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.components.staggeredEntrance
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    viewModel: VaultViewModel,
    onNavigateToItemDetail: (String) -> Unit,
    onNavigateToEditItem: (String) -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToSecurityCenter: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredItems.collectAsState()
    val allItems by viewModel.rawItems.collectAsState()
    val favorites by viewModel.favoriteItems.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val securityReport by viewModel.securityReport.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val visibleCategories by viewModel.visibleCategories.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val isMinimalistMode by viewModel.isMinimalistMode.collectAsState()

    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    val loginsCount = remember(allItems) { allItems.count { it.type == ItemType.LOGIN } }
    val cardsCount = remember(allItems) { allItems.count { it.type == ItemType.CREDIT_CARD } }
    val notesCount = remember(allItems) { allItems.count { it.type == ItemType.SECURE_NOTE } }
    val totpCount = remember(allItems) { allItems.count { it.totpSecret.isNotBlank() } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val isAutofillNudgeDismissed by viewModel.autofillNudgeDismissed.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedItemForActions by remember { mutableStateOf<VaultItem?>(null) }
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier
            .testTag("vault_dashboard_screen")
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                com.kryptx.app.core.designsystem.components.KryptxSnackbar(data)
            }
        },
        topBar = {
            if (isSelectionMode) {
                VaultSelectionTopBar(
                    selectedCount = selectedItemIds.size,
                    onClearSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAllFiltered() },
                    onBatchToggleFavorite = { viewModel.batchToggleFavorite() },
                    onBatchMoveToTrash = {
                        viewModel.batchMoveToTrash { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar("$count items moved to Trash")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .testTag("vault_add_item_fab")
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(KryptxElectricBlueGradient)
                        .border(1.dp, GlassmorphismSpecularBrush, CircleShape)
                        .bounceClick(scaleDown = 0.90f) { onNavigateToAddItem() }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Add new credential to vault"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Item",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideScreen = maxWidth >= 720.dp
            var selectedInspectorItemId by remember { mutableStateOf<String?>(null) }
            val selectedInspectorItem = remember(selectedInspectorItemId, allItems) {
                allItems.firstOrNull { it.id == selectedInspectorItemId }
            }

            androidx.compose.runtime.LaunchedEffect(isWideScreen, items) {
                if (isWideScreen && selectedInspectorItemId == null && items.isNotEmpty()) {
                    selectedInspectorItemId = items.first().id
                }
            }

            val focusManager = LocalFocusManager.current
            val listState = rememberLazyListState()

            androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) {
                    focusManager.clearFocus()
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .testTag("vault_items_list")
                        .then(if (isWideScreen) Modifier.width(380.dp) else Modifier.fillMaxWidth())
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                // 1. Top Header: User Profile Avatar + Welcome + Security Pulse + Lock Button
                item {
                    VaultDashboardHeader(
                        securityReport = securityReport,
                        onNavigateToSecurityCenter = onNavigateToSecurityCenter,
                        onLockVault = { viewModel.lockVault() }
                    )
                }

                // 1.5 Dismissible Autofill Setup Nudge
                if (!isAutofillNudgeDismissed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        AutofillNudgeCard(
                            context = context,
                            onDismiss = { viewModel.dismissAutofillNudge() }
                        )
                    }
                }

                if (allItems.isEmpty()) {
                    // Empty Vault Focused Hero Experience
                    item {
                        Spacer(modifier = Modifier.height(28.dp))
                        VaultEmptyHeroCard(
                            onNavigateToAddItem = onNavigateToAddItem
                        )
                    }
                } else {
                    // 2. Search Bar Capsule with inline expandable instant search
                    item {
                        VaultSearchBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            isSearchExpanded = isSearchExpanded,
                            onToggleSearchExpanded = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    viewModel.updateSearchQuery("")
                                }
                            },
                            onNavigateToSearch = onNavigateToSearch
                        )
                    }

            // 2.5 Subtle Kryptx Pulse hero card
            if (securityReport != null && securityReport!!.overallScore < 90) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    VaultSecurityAlertCard(
                        report = securityReport!!,
                        onClick = onNavigateToSecurityCenter
                    )
                }
            }

            // 3. Floating 3D Category Badges Hero Area
            item {
                Spacer(modifier = Modifier.height(14.dp))
                VaultCategoryBadges(
                    allItemsCount = allItems.size,
                    loginsCount = loginsCount,
                    cardsCount = cardsCount,
                    notesCount = notesCount,
                    totpCount = totpCount,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onNavigateTo2Fa = onNavigateToSecurityCenter,
                    visibleCategories = visibleCategories,
                    categoryCounts = categoryCounts
                )
            }

            // 4. Favorites section (when present & no active filters)
            if (favorites.isNotEmpty() && selectedCategory == null && searchQuery.isBlank()) {
                item {
                    VaultFavoritesSection(
                        favorites = favorites,
                        onNavigateToItemDetail = onNavigateToItemDetail,
                        onCopySecret = { favItem ->
                            viewModel.copySecret(favItem.title, favItem.primarySecret)
                            scope.launch {
                                snackbarHostState.showSnackbar("Secret copied! Clears automatically in 30s.")
                            }
                        }
                    )
                }
            }

            // 5. Main Signature WorkONE Folder Tab Card
            if (items.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        KryptxFolderCard(
                            title = when {
                                searchQuery.isNotBlank() -> "Search Results (${items.size})"
                                selectedCategory == null -> "All Items (${items.size})"
                                else -> "${selectedCategory!!.categoryName} (${items.size})"
                            }
                        ) {
                            KryptxEmptyState(
                                title = if (allItems.isEmpty()) "Your Vault is Empty" else "No matching items",
                                subtitle = if (allItems.isEmpty()) "Secure your logins, credit cards, identities, and notes in one place." else "Try adjusting your search query or select another category.",
                                actionButtonText = if (allItems.isEmpty()) "Add Your First Item" else null,
                                onActionClick = onNavigateToAddItem
                            )
                        }
                    }
                }
            } else {
                item(key = "vault_items_header") {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Section Header Title Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KryptxBlue.copy(alpha = 0.18f))
                                    .border(1.dp, KryptxBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(KryptxBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        searchQuery.isNotBlank() -> "Search Results (${items.size})"
                                        selectedCategory == null -> "All Items (${items.size})"
                                        else -> "${selectedCategory!!.categoryName} (${items.size})"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Smart Sort Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sort:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            VaultViewModel.SortOption.entries.forEach { opt ->
                                val isOptSelected = sortOption == opt
                                val optBgColor: Color = if (isOptSelected) KryptxBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                val optBorderColor: Color = if (isOptSelected) KryptxBrightBlue else Color.Transparent
                                val optTextColor: Color = if (isOptSelected) KryptxBrightBlue else MaterialTheme.colorScheme.onSurfaceVariant

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(optBgColor)
                                        .border(1.dp, optBorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            KryptxHaptics.tap(view)
                                            viewModel.setSortOption(opt)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = optTextColor
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id }
                ) { index, item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                when (dismissValue) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        // Swipe Right: 1-Touch Quick Copy
                                        if (item.primarySecret.isNotBlank()) {
                                            KryptxHaptics.confirm(view)
                                            com.kryptx.app.core.designsystem.components.KryptxAudio.snap(context)
                                            viewModel.copySecret(item.title, item.primarySecret)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Copied '${item.title}' secret (auto-clears in 30s)",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        } else {
                                            KryptxHaptics.tap(view)
                                        }
                                        // Return false so card springs back into place after copying
                                        false
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        // Swipe Left: Move to Trash / Delete
                                        KryptxHaptics.warning(view)
                                        val itemTitle = item.title
                                        viewModel.deleteItemWithUndo(item.id) { _ ->
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "'$itemTitle' deleted",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoLastDelete {
                                                        KryptxHaptics.confirm(view)
                                                    }
                                                }
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = !isSelectionMode,
                            enableDismissFromEndToStart = !isSelectionMode,
                            modifier = Modifier
                                .animateItem()
                                .staggeredEntrance(index = index),
                            backgroundContent = {
                                val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                                val isCopyGesture = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd

                                val swipeBg = if (isCopyGesture) {
                                    KryptxElectricBlueGradient
                                } else {
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(KryptxRed.copy(alpha = 0.7f), KryptxRed)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(swipeBg)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (isCopyGesture) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCopyGesture) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Secret",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Copy Secret",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete item",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Delete",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            val isInspectorSelected = isWideScreen && selectedInspectorItemId == item.id
                            Box(
                                modifier = if (isInspectorSelected) {
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.5.dp, KryptxBrightBlue, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                }
                            ) {
                                VaultItemRow(
                                    item = item,
                                    onClick = {
                                        if (isWideScreen) {
                                            selectedInspectorItemId = item.id
                                            com.kryptx.app.core.designsystem.components.KryptxAudio.click(context)
                                        } else {
                                            onNavigateToItemDetail(item.id)
                                        }
                                    },
                                    onLongClick = {
                                        KryptxHaptics.confirm(view)
                                        selectedItemForActions = item
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                                    onCopySecret = {
                                        viewModel.copySecret(item.title, item.primarySecret)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Password copied! Clears automatically in 30s.")
                                        }
                                    },
                                    isSelected = selectedItemIds.contains(item.id),
                                    isSelectionMode = isSelectionMode,
                                    onSelectToggle = { viewModel.toggleSelectItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )

        VaultInspectorPane(
            selectedItem = selectedInspectorItem,
            totalItemsCount = allItems.size,
            favoritesCount = favorites.size,
            totpCount = totpCount,
            securityReport = securityReport,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onNavigateToEdit = onNavigateToEditItem,
            onNavigateToAddItem = onNavigateToAddItem,
            onNavigateToSecurityCenter = onNavigateToSecurityCenter,
            onDeselectItem = { selectedInspectorItemId = null },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}
}
}

    // Quick Actions Context Menu Bottom Sheet on Card Long-Press
    selectedItemForActions?.let { actionItem ->
        VaultQuickActionsSheet(
            item = actionItem,
            sheetState = actionSheetState,
            onDismiss = { selectedItemForActions = null },
            onCopySecret = {
                viewModel.copySecret(it.title, it.primarySecret)
                scope.launch {
                    snackbarHostState.showSnackbar("Secret copied to secure clipboard!")
                }
            },
            onCopyUsername = {
                viewModel.copySecret("Username", it.username, timeoutSeconds = 60)
                scope.launch {
                    snackbarHostState.showSnackbar("Username copied!")
                }
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onEditItem = { onNavigateToEditItem(it) },
            onDeleteItem = { deletedItem ->
                val itemTitle = deletedItem.title
                viewModel.deleteItemWithUndo(deletedItem.id) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "'$itemTitle' deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoLastDelete()
                        }
                    }
                }
            }
        )
    }
}
