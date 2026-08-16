package com.kryptx.app.feature.vault

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.ItemTypeBadge
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBrandDiagonalGradient
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "Kryptx Vault",
                showBrandLogo = true,
                actions = {
                    // Security Pulse summary chip
                    if (securityReport != null) {
                        val score = securityReport!!.overallScore
                        val pulseColor = if (score >= 80) KryptxEmerald else KryptxAmber
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(pulseColor.copy(alpha = 0.15f))
                                .clickable { onNavigateToSecurityCenter() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                    }

                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { viewModel.lockVault() }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(KryptxBrandDiagonalGradient)
                    .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(20.dp))
                    .bounceClick(scaleDown = 0.90f) { onNavigateToAddItem() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Item",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Category Filter Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPill(
                        label = "All (${allItems.size})",
                        isSelected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                    ItemType.entries.forEach { type ->
                        val count = allItems.count { it.type == type }
                        CategoryPill(
                            label = "${type.categoryName} ($count)",
                            isSelected = selectedCategory == type,
                            onClick = { viewModel.selectCategory(type) }
                        )
                    }
                }
            }

            // Favorites section
            if (favorites.isNotEmpty() && selectedCategory == null) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Favorites",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favorites, key = { "fav_${it.id}" }) { favItem ->
                                FavoriteItemCard(
                                    item = favItem,
                                    onClick = { onNavigateToItemDetail(favItem.id) },
                                    onCopySecret = {
                                        viewModel.copySecret(favItem.title, favItem.primarySecret)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Secret copied! Clipboard will clear automatically.")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == null) "All Items" else selectedCategory!!.categoryName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${items.size} items",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Items List
            if (items.isEmpty()) {
                item {
                    KryptxEmptyState(
                        title = if (allItems.isEmpty()) "Your Vault is Empty" else "No matching items",
                        subtitle = if (allItems.isEmpty()) "Secure your logins, credit cards, identities, and notes in one place." else "Try selecting another category or clear your search.",
                        actionButtonText = if (allItems.isEmpty()) "Add Your First Item" else null,
                        onActionClick = onNavigateToAddItem
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    VaultItemRow(
                        item = item,
                        onClick = { onNavigateToItemDetail(item.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                        onCopySecret = {
                            viewModel.copySecret(item.title, item.primarySecret)
                            scope.launch {
                                snackbarHostState.showSnackbar("Password copied! Clears automatically.")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) KryptxCyan else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
        )
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(16.dp))
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .padding(12.dp)
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
                    contentDescription = null,
                    tint = KryptxAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.displaySubtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VaultItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopySecret: () -> Unit
) {
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemTypeBadge(type = item.type)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.displaySubtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) KryptxAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Secret",
                        tint = if (isCopied) KryptxEmerald else KryptxCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
