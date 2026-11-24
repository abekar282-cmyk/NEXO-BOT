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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexo.browser.AppSettings
import com.nexo.browser.BrowserEngine
import com.nexo.browser.EngineCapabilities
import com.nexo.browser.SearchEngine
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    capabilities: EngineCapabilities,
    onSearchEngineChange: (SearchEngine) -> Unit,
    onStrictProxyLockChange: (Boolean) -> Unit,
    onForceDarkChange: (Boolean) -> Unit,
    onWebRtcGuardChange: (Boolean) -> Unit,
    onTimeoutSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Browser & Proxy Security",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NexoColors.TextPrimary
                    )

                    SettingToggleRow(
                        title = "Strict SOCKS5 Proxy Lock",
                        subtitle = "Block all browser traffic if an assigned SOCKS5 proxy disconnects or fails authentication. Never fall back to direct IP.",
                        checked = settings.strictProxyLock,
                        accent = NexoColors.ElectricBlue,
                        onCheckedChange = onStrictProxyLockChange
                    )

                    HorizontalDivider(color = NexoColors.BorderSubtle.copy(alpha = 0.5f))

                    SettingToggleRow(
                        title = "WebRTC IP Leak Guard",
                        subtitle = "Disables RTCPeerConnection and getUserMedia JavaScript APIs on page start inside browser profiles.",
                        checked = settings.webrtcProtection,
                        accent = NexoColors.BlazeOrange,
                        onCheckedChange = onWebRtcGuardChange
                    )

                    HorizontalDivider(color = NexoColors.BorderSubtle.copy(alpha = 0.5f))

                    SettingToggleRow(
                        title = "Force Dark Web Content",
                        subtitle = "Applies Chromium algorithmic darkening to light websites.",
                        checked = settings.forceDarkWebContent,
                        accent = NexoColors.ElectricBlue,
                        onCheckedChange = onForceDarkChange
                    )

                    HorizontalDivider(color = NexoColors.BorderSubtle.copy(alpha = 0.5f))

                    val timeoutSec = (settings.proxyTimeoutMs / 1000f).roundToInt().coerceIn(4, 30)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "SOCKS5 Timeout",
                                style = MaterialTheme.typography.titleMedium,
                                color = NexoColors.TextPrimary
                            )
                            Text(
                                "${timeoutSec}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = NexoColors.BlazeOrange
                            )
                        }
                        Slider(
                            value = timeoutSec.toFloat(),
                            onValueChange = { onTimeoutSecondsChange(it.roundToInt()) },
                            valueRange = 4f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = NexoColors.BlazeOrange,
                                activeTrackColor = NexoColors.ElectricBlue
                            )
                        )
                    }
                }
            }
        }

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
                        "Default Search Engine",
                        style = MaterialTheme.typography.titleLarge,
                        color = NexoColors.TextPrimary
                    )
                    SearchEngine.entries.forEach { engine ->
                        val selected = settings.defaultSearchEngine == engine
                        FilterChip(
                            selected = selected,
                            onClick = { onSearchEngineChange(engine) },
                            label = { Text("${engine.label} (${engine.homeUrl})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexoColors.ElectricBlueSoft,
                                selectedLabelColor = Color.White,
                                containerColor = NexoColors.DeepSlate,
                                labelColor = NexoColors.TextSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

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
                        "Engine & Isolation Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        color = NexoColors.TextPrimary
                    )
                    DiagnosticRow("WebView Version", capabilities.webViewVersion, true)
                    DiagnosticRow("Multi-Profile Storage (ProfileStore)", if (capabilities.supportsMultiProfile) "Supported (Isolated)" else "Legacy Fallback", capabilities.supportsMultiProfile)
                    DiagnosticRow("ProxyController Override", if (capabilities.supportsProxyOverride) "Supported (Fail-Closed)" else "Unsupported", capabilities.supportsProxyOverride)
                    DiagnosticRow("Algorithmic Darkening", if (capabilities.supportsAlgorithmicDarkening) "Supported" else "Unavailable", capabilities.supportsAlgorithmicDarkening)

                    HorizontalDivider(color = NexoColors.BorderSubtle.copy(alpha = 0.5f))

                    BrowserEngine.architectureEvaluationNotes.forEach { (title, body) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = MaterialTheme.typography.labelLarge, color = NexoColors.BlazeOrange)
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = NexoColors.TextSecondary)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = NexoColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NexoColors.TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent)
        )
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NexoColors.TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) NexoColors.EmeraldOk else NexoColors.CoralError
        )
    }
}
