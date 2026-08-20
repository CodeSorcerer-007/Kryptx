package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.FolderStatPill
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.ItemTypeBadge
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxFolderCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.components.diagonalStripedMeter
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxIceBlue
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import com.kryptx.app.core.designsystem.theme.OledBackground
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.delay
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

    val loginsCount = remember(allItems) { allItems.count { it.type == ItemType.LOGIN } }
    val cardsCount = remember(allItems) { allItems.count { it.type == ItemType.CREDIT_CARD } }
    val notesCount = remember(allItems) { allItems.count { it.type == ItemType.SECURE_NOTE } }
    val totpCount = remember(allItems) { allItems.count { it.totpSecret.isNotBlank() } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current

    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedItemForActions by remember { mutableStateOf<VaultItem?>(null) }
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = OledBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // WorkONE Top Header: User Profile Avatar + "Welcome Back / Kryptx Vault" + Top Circular Actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Glowing Avatar
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F1B33))
                                .border(1.5.dp, KryptxBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = KryptxIceBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Welcome Back",
                                fontSize = 12.sp,
                                color = KryptxIceBlue.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Kryptx Vault",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Security Pulse score chip button
                        if (securityReport != null) {
                            val score = securityReport!!.overallScore
                            val pulseColor = if (score >= 80) KryptxEmerald else KryptxAmber
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pulseColor.copy(alpha = 0.15f))
                                    .border(1.dp, pulseColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .clickable { onNavigateToSecurityCenter() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "Security score $score percent"
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(pulseColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$score%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = pulseColor
                                )
                            }
                        } else {
                            KryptxCircleIconButton(
                                icon = Icons.Default.Notifications,
                                contentDescription = "Security Pulse",
                                onClick = onNavigateToSecurityCenter
                            )
                        }

                        KryptxCircleIconButton(
                            icon = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            onClick = { viewModel.lockVault() }
                        )
                    }
                }
            }

            // WorkONE Capsule Search Bar with Filter Slider Pill & Inline Expand support
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    viewModel.updateSearchQuery("")
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search items",
                                    tint = if (isSearchExpanded || searchQuery.isNotBlank()) KryptxCyan else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) searchQuery else "Search vault items, credentials...",
                                    fontSize = 14.sp,
                                    color = if (searchQuery.isNotBlank()) Color.White else Color.White.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.updateSearchQuery("") },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                        .clickable { onNavigateToSearch() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Open Advanced Search",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Inline Instant Search TextField when expanded
                    AnimatedVisibility(
                        visible = isSearchExpanded,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            KryptxTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = "Filter by title, username, domain, tag...",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = KryptxCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = if (searchQuery.isNotBlank()) {
                                    {
                                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search query",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // WorkONE Floating 3D Category Badges Hero Area
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingCategoryBadge(
                        label = "All",
                        badgeText = "ALL",
                        count = allItems.size,
                        color = KryptxBlue,
                        isSelected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                    FloatingCategoryBadge(
                        label = "Logins",
                        badgeText = "KEY",
                        count = loginsCount,
                        color = Color(0xFF1F75FE),
                        isSelected = selectedCategory == ItemType.LOGIN,
                        onClick = { viewModel.selectCategory(ItemType.LOGIN) }
                    )
                    FloatingCategoryBadge(
                        label = "Cards",
                        badgeText = "CARD",
                        count = cardsCount,
                        color = Color(0xFF388BFF),
                        isSelected = selectedCategory == ItemType.CREDIT_CARD,
                        onClick = { viewModel.selectCategory(ItemType.CREDIT_CARD) }
                    )
                    FloatingCategoryBadge(
                        label = "Notes",
                        badgeText = "NOTE",
                        count = notesCount,
                        color = Color(0xFF60A5FA),
                        isSelected = selectedCategory == ItemType.SECURE_NOTE,
                        onClick = { viewModel.selectCategory(ItemType.SECURE_NOTE) }
                    )
                    FloatingCategoryBadge(
                        label = "2FA",
                        badgeText = "TOTP",
                        count = totpCount,
                        color = Color(0xFF00D4FF),
                        isSelected = false,
                        onClick = onNavigateToSecurityCenter
                    )
                }
            }

            // Favorites section (if present)
            if (favorites.isNotEmpty() && selectedCategory == null && searchQuery.isBlank()) {
                item {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Favorites",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favorites, key = { "fav_${it.id}" }) { favItem ->
                                FavoriteItemCard(
                                    item = favItem,
                                    onClick = { onNavigateToItemDetail(favItem.id) },
                                    onCopySecret = {
                                        viewModel.copySecret(favItem.title, favItem.primarySecret)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Secret copied! Clears automatically in 30s.")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Main Signature WorkONE Folder Tab Card
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
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable { onNavigateToSearch() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Items",
                                    tint = Color.White,
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
                                items.forEach { item ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                                KryptxHaptics.warning(view)
                                                val itemTitle = item.title
                                                viewModel.deleteItemWithUndo(item.id) { deletedItem ->
                                                    scope.launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "'$itemTitle' deleted",
                                                            actionLabel = "Undo",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            viewModel.undoLastDelete { restored ->
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
                                        modifier = Modifier.animateItem(),
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
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // WorkONE Signature Storage & Health Meter Card (Striped Progress Bar)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                val score = securityReport?.overallScore ?: 100
                val storageRatio = if (allItems.isEmpty()) 0.05f else (items.size.toFloat() / allItems.size.coerceAtLeast(1).toFloat()).coerceIn(0.1f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0C1424).copy(alpha = 0.70f))
                        .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "${items.size} / ${allItems.size.coerceAtLeast(1)} Items",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = KryptxIceBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Striped Diagonal Animated Meter Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF070E1A))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(11.dp))
                                .diagonalStripedMeter(
                                    progress = storageRatio,
                                    stripeColor = KryptxBlue,
                                    stripeBgColor = Color(0xFF0F47A8)
                                )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vault Health: $score%",
                                fontSize = 12.sp,
                                color = if (score >= 80) KryptxEmerald else KryptxAmber,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "${(storageRatio * 100).toInt()}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Actions Context Menu Bottom Sheet on Card Long-Press
    selectedItemForActions?.let { actionItem ->
        ModalBottomSheet(
            onDismissRequest = { selectedItemForActions = null },
            sheetState = actionSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ItemTypeBadge(type = actionItem.type, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = actionItem.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = actionItem.displaySubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Copy Secret Action
                if (actionItem.primarySecret.isNotBlank()) {
                    QuickActionMenuRow(
                        icon = Icons.Default.ContentCopy,
                        iconTint = KryptxCyan,
                        title = "Copy Password / Secret",
                        subtitle = "Clipboard cleared automatically in 30s",
                        onClick = {
                            viewModel.copySecret(actionItem.title, actionItem.primarySecret)
                            selectedItemForActions = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Secret copied to secure clipboard!")
                            }
                        }
                    )
                }

                // Copy Username / Email
                if (actionItem.username.isNotBlank()) {
                    QuickActionMenuRow(
                        icon = Icons.Default.Person,
                        iconTint = KryptxViolet,
                        title = "Copy Username (${actionItem.username})",
                        subtitle = "Copy user identifier to clipboard",
                        onClick = {
                            viewModel.copySecret("Username", actionItem.username, timeoutSeconds = 60)
                            selectedItemForActions = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Username copied!")
                            }
                        }
                    )
                }

                // Toggle Favorite Action
                QuickActionMenuRow(
                    icon = if (actionItem.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    iconTint = KryptxAmber,
                    title = if (actionItem.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    subtitle = if (actionItem.isFavorite) "Starred item" else "Pin to top favorites section",
                    onClick = {
                        viewModel.toggleFavorite(actionItem.id)
                        selectedItemForActions = null
                    }
                )

                // Edit Item Action
                QuickActionMenuRow(
                    icon = Icons.Default.Edit,
                    iconTint = KryptxEmerald,
                    title = "Edit Credential",
                    subtitle = "Modify fields, passwords, tags, or attachments",
                    onClick = {
                        selectedItemForActions = null
                        onNavigateToItemDetail(actionItem.id)
                    }
                )

                // Open Website in browser if URL present
                if (actionItem.website.isNotBlank()) {
                    QuickActionMenuRow(
                        icon = Icons.Default.OpenInBrowser,
                        iconTint = KryptxCyan,
                        title = "Open Website",
                        subtitle = actionItem.website,
                        onClick = {
                            selectedItemForActions = null
                            try {
                                val url = if (!actionItem.website.startsWith("http://") && !actionItem.website.startsWith("https://")) {
                                    "https://" + actionItem.website
                                } else actionItem.website
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }

                // Delete Action (With Undo Snackbar)
                QuickActionMenuRow(
                    icon = Icons.Default.Delete,
                    iconTint = KryptxRed,
                    title = "Delete Item",
                    subtitle = "Remove this record with instant undo option",
                    onClick = {
                        val itemTitle = actionItem.title
                        selectedItemForActions = null
                        viewModel.deleteItemWithUndo(actionItem.id) {
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

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Quick action menu row for the long-press bottom sheet.
 */
@Composable
fun QuickActionMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                KryptxHaptics.tap(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 3D-styled floating category badge (WorkONE hero badge).
 */
@Composable
fun FloatingCategoryBadge(
    label: String,
    badgeText: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (isSelected) color else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                contentDescription = "Category $label"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$label ($count)",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun FavoriteItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    onCopySecret: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(145.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C1424).copy(alpha = 0.70f))
            .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(12.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Favorite item ${item.title}"
            }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemTypeBadge(type = item.type, modifier = Modifier.size(28.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = KryptxAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.displaySubtitle,
                fontSize = 11.sp,
                color = KryptxIceBlue.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VaultItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onCopySecret: () -> Unit
) {
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                role = Role.Button
                contentDescription = "${item.title}, ${item.type.categoryName}, ${item.displaySubtitle}"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemTypeBadge(type = item.type)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.displaySubtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite star button
            IconButton(
                onClick = {
                    KryptxHaptics.tap(view)
                    onToggleFavorite()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (item.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (item.isFavorite) KryptxAmber else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Quick Copy Secret button
            if (item.primarySecret.isNotBlank()) {
                IconButton(
                    onClick = {
                        KryptxHaptics.confirm(view)
                        isCopied = true
                        onCopySecret()
                        scope.launch {
                            delay(2000L)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Secret",
                        tint = if (isCopied) KryptxEmerald else KryptxBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
