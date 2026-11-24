package com.nexo.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexo.browser.ui.BulkImportScreen
import com.nexo.browser.ui.DashboardScreen
import com.nexo.browser.ui.NexoBrowserTheme
import com.nexo.browser.ui.NexoColors
import com.nexo.browser.ui.NexoViewModel
import com.nexo.browser.ui.ProxyVaultScreen
import com.nexo.browser.ui.SettingsScreen

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_PROXIES = "proxies"
const val ROUTE_IMPORT = "bulk_import"
const val ROUTE_SETTINGS = "settings"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexoBrowserTheme {
                NexoMainShell(
                    onOpenBrowserProfile = { profileId ->
                        val intent = Intent(this, BrowserActivity::class.java).apply {
                            putExtra(BrowserActivity.EXTRA_PROFILE_ID, profileId)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun NexoMainShell(
    onOpenBrowserProfile: (Long) -> Unit,
    viewModel: NexoViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bulkParseResult by viewModel.bulkParseResult.collectAsStateWithLifecycle()
    val isBulkImporting by viewModel.isBulkImporting.collectAsStateWithLifecycle()
    val bulkImportOutcome by viewModel.bulkImportOutcome.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(dashboardState.statusBanner) {
        val msg = dashboardState.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(ROUTE_DASHBOARD, "Profiles", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
            NavDestination(ROUTE_PROXIES, "SOCKS5", Icons.Filled.VpnKey, Icons.Outlined.VpnKey),
            NavDestination(ROUTE_IMPORT, "Import", Icons.Filled.UploadFile, Icons.Outlined.UploadFile),
            NavDestination(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(ROUTE_DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NexoColors.ObsidianBlack)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = NexoColors.ObsidianBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = NexoColors.DeepSlate,
                        contentColor = NexoColors.TextPrimary,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NexoColors.ElectricBlue,
                                    selectedTextColor = NexoColors.ElectricBlue,
                                    indicatorColor = NexoColors.ElectricBlueSoft,
                                    unselectedIconColor = NexoColors.TextSecondary,
                                    unselectedTextColor = NexoColors.TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = NexoColors.DeepSlate,
                        contentColor = NexoColors.TextPrimary
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NexoColors.ElectricBlue,
                                    selectedTextColor = NexoColors.ElectricBlue,
                                    indicatorColor = NexoColors.ElectricBlueSoft,
                                    unselectedIconColor = NexoColors.TextSecondary,
                                    unselectedTextColor = NexoColors.TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                state = dashboardState,
                                onSearchChange = viewModel::setSearchQuery,
                                onSortChange = viewModel::setSortOrder,
                                onOpenProfile = onOpenBrowserProfile,
                                onCreateProfile = viewModel::createProfile,
                                onUpdateProfile = viewModel::updateProfile,
                                onCloneProfile = viewModel::cloneProfile,
                                onDeleteProfile = viewModel::deleteProfile,
                                onClearProfileStorage = viewModel::clearProfileStorage,
                                onBatchCreate100 = { viewModel.batchCreateUpTo(ProfileManager.MAX_PROFILES) },
                                onTestProxy = viewModel::testProxy,
                                onNavigateToBulkImport = { navigateTo(ROUTE_IMPORT) }
                            )
                        }
                        composable(ROUTE_PROXIES) {
                            ProxyVaultScreen(
                                proxies = dashboardState.proxies,
                                decryptPassword = viewModel::decryptProxyPassword,
                                onAddOrUpdateProxy = viewModel::addOrUpdateProxy,
                                onDeleteProxy = viewModel::deleteProxy,
                                onTestProxy = viewModel::testProxy,
                                onTestAllProxies = viewModel::testAllProxies,
                                onAutoAssignProxies = viewModel::autoAssignProxies,
                                onOpenBulkImport = { navigateTo(ROUTE_IMPORT) }
                            )
                        }
                        composable(ROUTE_IMPORT) {
                            BulkImportScreen(
                                parseResult = bulkParseResult,
                                isImporting = isBulkImporting,
                                importOutcome = bulkImportOutcome,
                                proxies = dashboardState.proxies,
                                onAnalyzeText = viewModel::parseBulkText,
                                onExecuteImport = viewModel::executeBulkImport,
                                onClearPreview = viewModel::clearBulkPreview
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                capabilities = viewModel.engineCapabilities,
                                onSearchEngineChange = viewModel::setDefaultSearchEngine,
                                onStrictProxyLockChange = viewModel::setStrictProxyLock,
                                onForceDarkChange = viewModel::setForceDarkWebContent,
                                onWebRtcGuardChange = viewModel::setWebRtcProtection,
                                onTimeoutSecondsChange = viewModel::setProxyTimeoutSeconds
                            )
                        }
                    }
                }
            }
        }
    }
}
