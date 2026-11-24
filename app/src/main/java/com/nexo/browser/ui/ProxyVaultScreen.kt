package com.nexo.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.browser.ProxyEntity
import com.nexo.browser.ProxyStatus

@Composable
fun ProxyVaultScreen(
    proxies: List<ProxyEntity>,
    decryptPassword: (ProxyEntity) -> String,
    onAddOrUpdateProxy: (Long?, String, String, Int, String, String) -> Unit,
    onDeleteProxy: (Long) -> Unit,
    onTestProxy: (Long) -> Unit,
    onTestAllProxies: () -> Unit,
    onAutoAssignProxies: () -> Unit,
    onOpenBulkImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<ProxyEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexoColors.ObsidianBlack)
            .testTag("proxy_vault_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NexoColors.CardSurface,
                border = BorderStroke(1.dp, NexoColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "SOCKS5 Proxy Vault",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NexoColors.TextPrimary
                            )
                            Text(
                                "AES-256-GCM Encrypted • ${proxies.size} stored",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextSecondary
                            )
                        }
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Encrypted storage",
                            tint = NexoColors.EmeraldOk,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_proxy_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add SOCKS5")
                        }

                        OutlinedButton(
                            onClick = onOpenBulkImport,
                            border = BorderStroke(1.dp, NexoColors.BlazeOrange),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.BlazeOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("bulk_import_nav_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bulk TXT")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestAllProxies,
                            border = BorderStroke(1.dp, NexoColors.ElectricBlue.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.ElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("test_all_proxies_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test All")
                        }

                        OutlinedButton(
                            onClick = onAutoAssignProxies,
                            border = BorderStroke(1.dp, NexoColors.EmeraldOk.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.EmeraldOk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("auto_assign_proxies_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto-Assign")
                        }
                    }
                }
            }
        }

        if (proxies.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NexoColors.CardSurface,
                    border = BorderStroke(1.dp, NexoColors.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = NexoColors.BlazeOrange,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "No SOCKS5 Proxies Configured",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexoColors.TextPrimary
                        )
                        Text(
                            "Add an authenticated SOCKS5 proxy manually or bulk-import up to 100 proxies from a TXT file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            items(proxies, key = { it.id }) { proxy ->
                ProxyVaultCard(
                    proxy = proxy,
                    onTest = { onTestProxy(proxy.id) },
                    onEdit = { editingProxy = proxy },
                    onDelete = { onDeleteProxy(proxy.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }

    if (showAddDialog) {
        ProxyEditorDialog(
            title = "Add SOCKS5 Proxy",
            initialName = "",
            initialHost = "",
            initialPort = "1080",
            initialUsername = "",
            initialPassword = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, host, port, user, pass ->
                onAddOrUpdateProxy(null, name, host, port, user, pass)
                showAddDialog = false
            }
        )
    }

    editingProxy?.let { proxy ->
        ProxyEditorDialog(
            title = "Edit SOCKS5 Proxy",
            initialName = proxy.name,
            initialHost = proxy.host,
            initialPort = proxy.port.toString(),
            initialUsername = proxy.username,
            initialPassword = decryptPassword(proxy),
            onDismiss = { editingProxy = null },
            onSave = { name, host, port, user, pass ->
                onAddOrUpdateProxy(proxy.id, name, host, port, user, pass)
                editingProxy = null
            }
        )
    }
}

@Composable
private fun ProxyVaultCard(
    proxy: ProxyEntity,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (badgeColor, badgeText) = when (proxy.status) {
        ProxyStatus.WORKING -> NexoColors.EmeraldOk to "WORKING"
        ProxyStatus.CONNECTED -> NexoColors.ElectricBlue to "CONNECTED"
        ProxyStatus.FAILED -> NexoColors.CoralError to "FAILED"
        ProxyStatus.TESTING -> NexoColors.AmberWarn to "TESTING"
        ProxyStatus.UNTESTED -> NexoColors.TextMuted to "UNTESTED"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NexoColors.CardSurface),
        border = BorderStroke(1.dp, NexoColors.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("proxy_card_${proxy.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = proxy.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = NexoColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SOCKS5 • ${proxy.host}:${proxy.port}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexoColors.ElectricBlue
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (proxy.status == ProxyStatus.TESTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = badgeColor
                            )
                        } else if (proxy.status == ProxyStatus.WORKING || proxy.status == ProxyStatus.CONNECTED) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = badgeColor, modifier = Modifier.size(13.dp))
                        } else if (proxy.status == ProxyStatus.FAILED) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = badgeColor, modifier = Modifier.size(13.dp))
                        }
                        Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = NexoColors.TextSecondary, modifier = Modifier.size(15.dp))
                    Text(
                        text = if (proxy.publicIp != null) {
                            val cc = proxy.countryCode?.let { " [$it]" }.orEmpty()
                            "Exit IP: ${proxy.publicIp}$cc"
                        } else {
                            "Exit IP: Not checked"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = NexoColors.TextSecondary
                    )
                }

                if (proxy.latencyMs != null) {
                    Text(
                        text = "${proxy.latencyMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = NexoColors.EmeraldOk
                    )
                }
            }

            if (proxy.username.isNotBlank()) {
                Text(
                    text = "Auth: ${proxy.username} • Password Encrypted (AES-GCM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoColors.TextMuted
                )
            }

            if (!proxy.lastError.isNullOrBlank()) {
                Text(
                    text = "Error: ${proxy.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexoColors.CoralError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = proxy.status != ProxyStatus.TESTING,
                    border = BorderStroke(1.dp, NexoColors.BlazeOrange.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.BlazeOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test & Check IP")
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoColors.CardSurfaceHigh)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit proxy", tint = NexoColors.TextPrimary)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexoColors.CoralSoft)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete proxy", tint = NexoColors.CoralError)
                }
            }
        }
    }
}

@Composable
private fun ProxyEditorDialog(
    title: String,
    initialName: String,
    initialHost: String,
    initialPort: String,
    initialUsername: String,
    initialPassword: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexoColors.CardSurfaceHigh,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; errorText = null },
                        label = { Text("IP / Host") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("proxy_host_input")
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit); errorText = null },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("proxy_port_input")
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("SOCKS5 Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_user_input")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("SOCKS5 Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_pass_input")
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoColors.CoralError
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull()
                    if (host.isBlank()) {
                        errorText = "Enter a valid proxy IP or hostname"
                    } else if (port == null || port !in 1..65535) {
                        errorText = "Port must be between 1 and 65535"
                    } else {
                        onSave(name, host, port, username, password)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                modifier = Modifier.testTag("save_proxy_button")
            ) {
                Text("Save SOCKS5")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexoColors.TextSecondary)
            }
        }
    )
}
