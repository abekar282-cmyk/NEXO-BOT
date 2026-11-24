package com.nexo.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.nexo.browser.ActiveProxyTunnel
import com.nexo.browser.ProfileManager
import com.nexo.browser.ProfileStatus
import com.nexo.browser.ProfileWithProxy
import com.nexo.browser.ProxyEntity
import com.nexo.browser.ProxyStatus
import com.nexo.browser.UserAgentPreset

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onSearchChange: (String) -> Unit,
    onSortChange: (ProfileSortOrder) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onCreateProfile: (String, Long?, UserAgentPreset, Boolean, String) -> Unit,
    onUpdateProfile: (Long, String, Long?, UserAgentPreset, Boolean, String) -> Unit,
    onCloneProfile: (Long) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onClearProfileStorage: (Long) -> Unit,
    onBatchCreate100: () -> Unit,
    onTestProxy: (Long) -> Unit,
    onNavigateToBulkImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<ProfileWithProxy?>(null) }
    var deletingTarget by remember { mutableStateOf<ProfileWithProxy?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NexoColors.ObsidianBlack)
    ) {
        val columns = when {
            maxWidth >= 960.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_profile_grid")
        ) {
            item(span = { GridItemSpan(columns) }) {
                DashboardHeroHeader(
                    totalProfiles = state.totalProfilesCount,
                    activeProfiles = state.activeProfilesCount,
                    workingProxies = state.workingProxiesCount,
                    onNewProfileClick = { showCreateDialog = true },
                    onFill100Click = onBatchCreate100,
                    onImportProxiesClick = onNavigateToBulkImport
                )
            }

            item(span = { GridItemSpan(columns) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_search_input"),
                        placeholder = {
                            Text(
                                "Search by profile name, #number, IP, or proxy...",
                                color = NexoColors.TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search profiles",
                                tint = NexoColors.ElectricBlue
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = NexoColors.TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexoColors.ElectricBlue,
                            unfocusedBorderColor = NexoColors.BorderSubtle,
                            focusedContainerColor = NexoColors.CardSurface,
                            unfocusedContainerColor = NexoColors.CardSurface,
                            focusedTextColor = NexoColors.TextPrimary,
                            unfocusedTextColor = NexoColors.TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileSortOrder.entries.forEach { order ->
                            val selected = state.sortOrder == order
                            FilterChip(
                                selected = selected,
                                onClick = { onSortChange(order) },
                                label = {
                                    Text(
                                        order.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NexoColors.ElectricBlueSoft,
                                    selectedLabelColor = Color.White,
                                    containerColor = NexoColors.CardSurface,
                                    labelColor = NexoColors.TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = NexoColors.BorderSubtle,
                                    selectedBorderColor = NexoColors.ElectricBlue
                                ),
                                modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (state.filteredProfiles.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    EmptyProfilesCard(
                        hasQuery = state.searchQuery.isNotBlank(),
                        onCreateProfile = { showCreateDialog = true }
                    )
                }
            } else {
                items(
                    items = state.filteredProfiles,
                    key = { it.profile.id }
                ) { item ->
                    ProfileCard(
                        item = item,
                        activeTunnel = state.activeTunnels[item.profile.id],
                        onOpenBrowser = { onOpenProfile(item.profile.id) },
                        onEdit = { editingTarget = item },
                        onClone = { onCloneProfile(item.profile.id) },
                        onDelete = { deletingTarget = item },
                        onTestProxy = { item.proxy?.id?.let(onTestProxy) }
                    )
                }
            }

            item(span = { GridItemSpan(columns) }) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showCreateDialog) {
        ProfileEditorDialog(
            title = "Create Isolated Profile",
            initialName = "Profile ${(state.totalProfilesCount + 1).toString().padStart(2, '0')}",
            initialProxyId = null,
            initialUserAgent = UserAgentPreset.MOBILE_CHROME,
            initialIncognito = false,
            initialNotes = "",
            proxies = state.proxies,
            onDismiss = { showCreateDialog = false },
            onSave = { name, proxyId, ua, incognito, notes ->
                onCreateProfile(name, proxyId, ua, incognito, notes)
                showCreateDialog = false
            }
        )
    }

    editingTarget?.let { target ->
        val ua = runCatching { UserAgentPreset.valueOf(target.profile.userAgentPreset) }
            .getOrDefault(UserAgentPreset.MOBILE_CHROME)
        ProfileEditorDialog(
            title = "Edit Profile #${target.profile.profileNumber.toString().padStart(3, '0')}",
            initialName = target.profile.name,
            initialProxyId = target.profile.proxyId,
            initialUserAgent = ua,
            initialIncognito = target.profile.isIncognitoDefault,
            initialNotes = target.profile.notes,
            proxies = state.proxies,
            onDismiss = { editingTarget = null },
            onClearStorage = {
                onClearProfileStorage(target.profile.id)
                editingTarget = null
            },
            onSave = { name, proxyId, newUa, incognito, notes ->
                onUpdateProfile(target.profile.id, name, proxyId, newUa, incognito, notes)
                editingTarget = null
            }
        )
    }

    deletingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTarget = null },
            containerColor = NexoColors.CardSurfaceHigh,
            title = {
                Text(
                    "Delete ${target.profile.name}?",
                    color = NexoColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    "This permanently deletes Profile #${target.profile.profileNumber} along with its isolated cookies, cache, LocalStorage, history, bookmarks, and saved tabs.",
                    color = NexoColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(target.profile.id)
                        deletingTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoColors.CoralError)
                ) {
                    Text("Delete Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTarget = null }) {
                    Text("Cancel", color = NexoColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DashboardHeroHeader(
    totalProfiles: Int,
    activeProfiles: Int,
    workingProxies: Int,
    onNewProfileClick: () -> Unit,
    onFill100Click: () -> Unit,
    onImportProxiesClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NexoColors.CardSurface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(NexoColors.ElectricBlue.copy(alpha = 0.65f), NexoColors.BlazeOrange.copy(alpha = 0.55f))
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0E1B36),
                            NexoColors.CardSurface,
                            Color(0xFF21130A)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.nexo_zone_logo_1795481539206),
                            contentDescription = "NEXO ZONE emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column {
                            Text(
                                text = "NEXO BROWSER",
                                style = MaterialTheme.typography.titleLarge,
                                color = NexoColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Isolated Multi-Profile SOCKS5 Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = NexoColors.ElectricBlueSoft,
                        border = BorderStroke(1.dp, NexoColors.ElectricBlue.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "$totalProfiles / ${ProfileManager.MAX_PROFILES}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniTile(
                        label = "Profiles",
                        value = "$totalProfiles",
                        accent = NexoColors.ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniTile(
                        label = "Active",
                        value = "$activeProfiles",
                        accent = NexoColors.EmeraldOk,
                        modifier = Modifier.weight(1f)
                    )
                    StatMiniTile(
                        label = "Proxies OK",
                        value = "$workingProxies",
                        accent = NexoColors.BlazeOrange,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNewProfileClick,
                        enabled = totalProfiles < ProfileManager.MAX_PROFILES,
                        colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("create_profile_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Profile", maxLines = 1)
                    }

                    if (totalProfiles < ProfileManager.MAX_PROFILES) {
                        OutlinedButton(
                            onClick = onFill100Click,
                            border = BorderStroke(1.dp, NexoColors.BlazeOrange.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.BlazeOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("fill_100_profiles_button")
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fill 100")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onImportProxiesClick,
                            border = BorderStroke(1.dp, NexoColors.BlazeOrange.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.BlazeOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("quick_import_proxies_button")
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexoColors.ObsidianBlack.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, NexoColors.BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = NexoColors.TextMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileCard(
    item: ProfileWithProxy,
    activeTunnel: ActiveProxyTunnel?,
    onOpenBrowser: () -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onTestProxy: () -> Unit
) {
    val profile = item.profile
    val proxy = item.proxy
    val isLiveActive = activeTunnel?.isConnected == true || profile.status == ProfileStatus.ACTIVE
    val isProxyError = activeTunnel?.lastError != null ||
        profile.status == ProfileStatus.PROXY_ERROR ||
        proxy?.status == ProxyStatus.FAILED

    val statusColor = when {
        isLiveActive -> NexoColors.EmeraldOk
        isProxyError -> NexoColors.CoralError
        proxy != null -> NexoColors.ElectricBlue
        else -> NexoColors.BlazeOrange
    }
    val statusText = when {
        isLiveActive -> "ACTIVE TUNNEL"
        isProxyError -> "PROXY ERROR"
        proxy != null -> "SOCKS5 READY"
        else -> "DIRECT / NO PROXY"
    }

    val displayedIp = activeTunnel?.publicIp
        ?: proxy?.publicIp
        ?: profile.lastPublicIp
        ?: if (proxy == null) "Local Network" else "Unverified IP"

    val proxyLabel = if (proxy != null) {
        "socks5://${proxy.host}:${proxy.port}"
    } else {
        "No proxy assigned"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NexoColors.CardSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isLiveActive) NexoColors.ElectricBlue else NexoColors.BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_card_${profile.profileNumber}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: #Number + Profile Name + Connection Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NexoColors.ElectricBlueSoft,
                        border = BorderStroke(1.dp, NexoColors.ElectricBlue.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "#${profile.profileNumber.toString().padStart(3, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = NexoColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ID: ${profile.storageKey}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            // Proxy & Public IP telemetry box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NexoColors.DeepSlate,
                border = BorderStroke(1.dp, NexoColors.BorderSubtle.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Assigned proxy",
                            tint = if (proxy != null) NexoColors.ElectricBlue else NexoColors.BlazeOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = proxyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = NexoColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (proxy != null) {
                            IconButton(
                                onClick = onTestProxy,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Test proxy IP",
                                    tint = NexoColors.BlazeOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Public IP",
                            tint = if (proxy?.publicIp != null || activeTunnel?.publicIp != null) NexoColors.EmeraldOk else NexoColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "IP: $displayedIp",
                            style = MaterialTheme.typography.labelMedium,
                            color = NexoColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val latency = proxy?.latencyMs ?: profile.lastLatencyMs
                        if (latency != null) {
                            Text(
                                text = "${latency}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexoColors.EmeraldOk
                            )
                        }
                    }
                }
            }

            // Actions Row: Open Browser + Edit + Clone + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenBrowser,
                    colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_browser_btn_${profile.profileNumber}")
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Browser", maxLines = 1)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoColors.CardSurfaceHigh)
                        .testTag("edit_profile_btn_${profile.profileNumber}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit profile",
                        tint = NexoColors.TextPrimary
                    )
                }

                IconButton(
                    onClick = onClone,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoColors.CardSurfaceHigh)
                        .testTag("clone_profile_btn_${profile.profileNumber}")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Clone profile",
                        tint = NexoColors.BlazeOrange
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoColors.CoralSoft)
                        .testTag("delete_profile_btn_${profile.profileNumber}")
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete profile",
                        tint = NexoColors.CoralError
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesCard(
    hasQuery: Boolean,
    onCreateProfile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = NexoColors.CardSurface,
        border = BorderStroke(1.dp, NexoColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = NexoColors.ElectricBlue,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = if (hasQuery) "No matching profiles" else "No profiles created yet",
                style = MaterialTheme.typography.titleLarge,
                color = NexoColors.TextPrimary
            )
            Text(
                text = if (hasQuery) "Try searching by #number, proxy host, or public IP."
                else "Create isolated browser profiles with independent cookies, storage, and SOCKS5 proxies.",
                style = MaterialTheme.typography.bodyMedium,
                color = NexoColors.TextSecondary
            )
            if (!hasQuery) {
                Button(
                    onClick = onCreateProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue)
                ) {
                    Text("Create First Profile")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorDialog(
    title: String,
    initialName: String,
    initialProxyId: Long?,
    initialUserAgent: UserAgentPreset,
    initialIncognito: Boolean,
    initialNotes: String,
    proxies: List<ProxyEntity>,
    onDismiss: () -> Unit,
    onClearStorage: (() -> Unit)? = null,
    onSave: (String, Long?, UserAgentPreset, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedProxyId by remember { mutableStateOf(initialProxyId) }
    var selectedUa by remember { mutableStateOf(initialUserAgent) }
    var incognitoDefault by remember { mutableStateOf(initialIncognito) }
    var notes by remember { mutableStateOf(initialNotes) }
    var proxyMenuExpanded by remember { mutableStateOf(false) }
    var uaMenuExpanded by remember { mutableStateOf(false) }

    val selectedProxyLabel = proxies.firstOrNull { it.id == selectedProxyId }?.let {
        "${it.name} (${it.host}:${it.port})"
    } ?: "No Proxy (Direct)"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexoColors.CardSurfaceHigh,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = proxyMenuExpanded,
                    onExpandedChange = { proxyMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProxyLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assigned SOCKS5 Proxy") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = proxyMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = proxyMenuExpanded,
                        onDismissRequest = { proxyMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Proxy (Direct)") },
                            onClick = {
                                selectedProxyId = null
                                proxyMenuExpanded = false
                            }
                        )
                        proxies.forEach { proxy ->
                            DropdownMenuItem(
                                text = {
                                    Text("${proxy.name} (${proxy.host}:${proxy.port}) • ${proxy.status.name}")
                                },
                                onClick = {
                                    selectedProxyId = proxy.id
                                    proxyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = uaMenuExpanded,
                    onExpandedChange = { uaMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedUa.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Browser User-Agent") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uaMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = uaMenuExpanded,
                        onDismissRequest = { uaMenuExpanded = false }
                    ) {
                        UserAgentPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = {
                                    selectedUa = preset
                                    uaMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Open in Incognito by default",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoColors.TextPrimary
                    )
                    Switch(
                        checked = incognitoDefault,
                        onCheckedChange = { incognitoDefault = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoColors.IncognitoPurple)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onClearStorage != null) {
                    OutlinedButton(
                        onClick = onClearStorage,
                        border = BorderStroke(1.dp, NexoColors.CoralError.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.CoralError),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wipe Profile Cookies, Cache & History")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedProxyId, selectedUa, incognitoDefault, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexoColors.TextSecondary)
            }
        }
    )
}
