package com.nexo.browser.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.nexo.browser.BulkImportOutcome
import com.nexo.browser.BulkProxyParseResult
import com.nexo.browser.ProxyEntity
import com.nexo.browser.ProxyStatus
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun BulkImportScreen(
    parseResult: BulkProxyParseResult?,
    isImporting: Boolean,
    importOutcome: BulkImportOutcome?,
    proxies: List<ProxyEntity>,
    onAnalyzeText: (String) -> Unit,
    onExecuteImport: (autoAssign: Boolean, autoTest: Boolean) -> Unit,
    onClearPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var loadedFileName by remember { mutableStateOf<String?>(null) }
    var autoAssign by remember { mutableStateOf(true) }
    var autoTest by remember { mutableStateOf(true) }

    val txtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }.onSuccess { contents ->
                if (contents != null) {
                    rawText = contents
                    loadedFileName = uri.lastPathSegment ?: "proxies.txt"
                    onAnalyzeText(contents)
                }
            }
        }
    }

    val importedSubset = remember(importOutcome, proxies) {
        val ids = importOutcome?.importedIds?.toSet().orEmpty()
        proxies.filter { it.id in ids }
    }
    val workingCount = importedSubset.count { it.status == ProxyStatus.WORKING || it.status == ProxyStatus.CONNECTED }
    val failedCount = importedSubset.count { it.status == ProxyStatus.FAILED }
    val testingCount = importedSubset.count { it.status == ProxyStatus.TESTING }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexoColors.ObsidianBlack),
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Bulk SOCKS5 Proxy Import",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NexoColors.TextPrimary
                    )
                    Text(
                        "Import up to 100 authenticated SOCKS5 proxies from a .txt file or paste below. Credentials are encrypted with AES-256-GCM.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexoColors.TextSecondary
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexoColors.DeepSlate,
                        border = BorderStroke(1.dp, NexoColors.BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Supported Formats:", style = MaterialTheme.typography.labelLarge, color = NexoColors.BlazeOrange)
                            Text(
                                "1) socks5://username:password@ip:port",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexoColors.TextPrimary
                            )
                            Text(
                                "2) ip:port:username:password",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexoColors.TextPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { txtPicker.launch(arrayOf("text/plain", "text/*")) },
                            border = BorderStroke(1.dp, NexoColors.ElectricBlue),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexoColors.ElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_txt_file_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(loadedFileName ?: "Select .TXT File", maxLines = 1)
                        }

                        Button(
                            onClick = { onAnalyzeText(rawText) },
                            enabled = rawText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = NexoColors.BlazeOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("validate_bulk_button")
                        ) {
                            Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Validate")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    onClearPreview()
                },
                label = { Text("Paste up to 100 SOCKS5 proxy lines") },
                placeholder = {
                    Text(
                        "socks5://user:pass@198.51.100.10:1080\n203.0.113.25:1080:user:pass",
                        color = NexoColors.TextMuted
                    )
                },
                minLines = 6,
                maxLines = 10,
                textStyle = MaterialTheme.typography.labelMedium,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexoColors.ElectricBlue,
                    unfocusedBorderColor = NexoColors.BorderSubtle,
                    focusedContainerColor = NexoColors.CardSurface,
                    unfocusedContainerColor = NexoColors.CardSurface,
                    focusedTextColor = NexoColors.TextPrimary,
                    unfocusedTextColor = NexoColors.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_proxy_text_input")
            )
        }

        if (parseResult != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = NexoColors.CardSurface,
                    border = BorderStroke(1.dp, NexoColors.ElectricBlue.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Validation Report",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexoColors.TextPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ValidationBadge(
                                label = "Valid",
                                count = parseResult.validEntries.size,
                                color = NexoColors.EmeraldOk,
                                modifier = Modifier.weight(1f)
                            )
                            ValidationBadge(
                                label = "Duplicates",
                                count = parseResult.duplicateLines.size,
                                color = NexoColors.AmberWarn,
                                modifier = Modifier.weight(1f)
                            )
                            ValidationBadge(
                                label = "Invalid",
                                count = parseResult.invalidLines.size,
                                color = NexoColors.CoralError,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (parseResult.truncatedCount > 0) {
                            Text(
                                "Note: ${parseResult.truncatedCount} lines skipped (max 100 per batch).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.AmberWarn
                            )
                        }

                        if (parseResult.invalidLines.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Invalid Lines (first 5):",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NexoColors.CoralError
                                )
                                parseResult.invalidLines.take(5).forEach { err ->
                                    Text(
                                        "Line ${err.lineNumber}: ${err.reason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexoColors.TextSecondary
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
                                "Auto-assign to profiles (1-to-1)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextPrimary
                            )
                            Switch(
                                checked = autoAssign,
                                onCheckedChange = { autoAssign = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = NexoColors.ElectricBlue)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Test imported proxies & verify Public IP",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextPrimary
                            )
                            Switch(
                                checked = autoTest,
                                onCheckedChange = { autoTest = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = NexoColors.BlazeOrange)
                            )
                        }

                        Button(
                            onClick = { onExecuteImport(autoAssign, autoTest) },
                            enabled = parseResult.validEntries.isNotEmpty() && !isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_bulk_import_button")
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Encrypting & Importing...")
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import ${parseResult.validEntries.size} Encrypted Proxies")
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = importOutcome != null) {
                if (importOutcome != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = NexoColors.CardSurfaceHigh,
                        border = BorderStroke(1.dp, NexoColors.EmeraldOk.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Import Complete",
                                style = MaterialTheme.typography.titleLarge,
                                color = NexoColors.EmeraldOk
                            )
                            Text(
                                "Imported: ${importOutcome.importedCount} • Assigned to Profiles: ${importOutcome.assignedCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ValidationBadge("Working", workingCount, NexoColors.EmeraldOk, Modifier.weight(1f))
                                ValidationBadge("Failed", failedCount, NexoColors.CoralError, Modifier.weight(1f))
                                ValidationBadge("Testing", testingCount, NexoColors.AmberWarn, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun ValidationBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = NexoColors.TextSecondary)
        }
    }
}
