package com.kryptx.app.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
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
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.components.staggeredEntrance
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    viewModel: VaultViewModel,
    onNavigateToItemDetail: (String) -> Unit,
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

    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedItemForActions by remember { mutableStateOf<VaultItem?>(null) }
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel Selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${selectedItemIds.size} Selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.TextButton(onClick = { viewModel.selectAllFiltered() }) {
                        Text("Select All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    androidx.compose.material3.IconButton(onClick = { viewModel.batchToggleFavorite() }) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Batch Favorite",
                            tint = com.kryptx.app.core.designsystem.theme.KryptxAmber
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = {
                            viewModel.batchMoveToTrash { count ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("$count items moved to Trash")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Batch Move to Trash",
                            tint = KryptxRed
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isWideScreen) Modifier.width(760.dp) else Modifier.fillMaxWidth()),
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(KryptxRed.copy(alpha = 0.08f))
                            .border(1.dp, KryptxRed.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToSecurityCenter() }
                            .padding(14.dp)
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "Action Required. ${securityReport!!.issues.size} security issues found in your vault. Double tap to review in Security Center."
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(KryptxRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = KryptxRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Action Required",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KryptxRed
                                )
                                Text(
                                    text = "${securityReport!!.issues.size} security issues found in your vault",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    KryptxFolderCard(
                        title = when {
                            searchQuery.isNotBlank() -> "Search Results (${items.size})"
                            selectedCategory == null -> "Documents"
                            else -> selectedCategory!!.categoryName
                        },
                        tabTrailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                                    .clickable { onNavigateToSearch() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Items",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        footerContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FolderStatPill(
                                    icon = Icons.Default.Lock,
                                    count = loginsCount,
                                    label = "Logins",
                                    modifier = Modifier.weight(1f)
                                )
                                FolderStatPill(
                                    icon = Icons.Default.CreditCard,
                                    count = cardsCount,
                                    label = "Cards",
                                    modifier = Modifier.weight(1f)
                                )
                                FolderStatPill(
                                    icon = Icons.AutoMirrored.Filled.Note,
                                    count = notesCount,
                                    label = "Notes",
                                    modifier = Modifier.weight(1f)
                                )
                                FolderStatPill(
                                    icon = Icons.Default.Key,
                                    count = totpCount,
                                    label = "2FA",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    ) {
                        if (items.isEmpty()) {
                            KryptxEmptyState(
                                title = if (allItems.isEmpty()) "Your Vault is Empty" else "No matching items",
                                subtitle = if (allItems.isEmpty()) "Secure your logins, credit cards, identities, and notes in one place." else "Try adjusting your search query or select another category.",
                                actionButtonText = if (allItems.isEmpty()) "Add Your First Item" else null,
                                onActionClick = onNavigateToAddItem
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Smart Sort Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sort:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    VaultViewModel.SortOption.values().forEach { opt ->
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

                                items.forEachIndexed { index, item ->
                                    @Suppress("DEPRECATION")
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
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
                                            } else {
                                                false
                                            }
                                        }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        modifier = Modifier
                                            .animateItem()
                                            .staggeredEntrance(index = index),
                                        backgroundContent = {
                                            val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(KryptxRed.copy(alpha = if (isDismissing) 0.85f else 0.4f))
                                                    .padding(horizontal = 20.dp),
                                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    ) {
                                        VaultItemRow(
                                            item = item,
                                            onClick = { onNavigateToItemDetail(item.id) },
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
            onEditItem = { onNavigateToItemDetail(it) },
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
