package com.nexo.browser

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexo.browser.ui.NexoBrowserTheme
import com.nexo.browser.ui.NexoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "https://www.google.com",
    val isIncognito: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

enum class BrowserSheet {
    NONE,
    TABS,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SEARCH_ENGINE
}

data class BrowserUiState(
    val profileWithProxy: ProfileWithProxy? = null,
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String = "",
    val proxyTunnelReady: Boolean = false,
    val isConnectingProxy: Boolean = true,
    val localBridgePort: Int? = null,
    val verifiedExitIp: String? = null,
    val proxyBlockedReason: String? = null,
    val desktopMode: Boolean = false,
    val activeSearchEngine: SearchEngine = SearchEngine.GOOGLE,
    val snackbarMessage: String? = null
) {
    val activeTab: BrowserTab? get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()
}

class BrowserSessionViewModel(
    application: Application,
    private val profileId: Long
) : AndroidViewModel(application) {

    private val app = application as NexoApplication
    private val database = app.database
    private val profileDao = database.profileDao()
    private val bookmarkDao = database.bookmarkDao()
    private val historyDao = database.historyDao()
    private val downloadDao = database.downloadDao()
    private val savedTabDao = database.savedTabDao()
    private val proxyManager = app.proxyManager
    private val profileManager = app.profileManager
    private val settingsStore = app.settingsStore

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val bookmarks = bookmarkDao.observeForProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = historyDao.observeForProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads = downloadDao.observeForProfile(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    init {
        initializeSession()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            val loadedSettings = settingsStore.settingsFlow
            var currentSettings = AppSettings()
            launch {
                loadedSettings.collect { s ->
                    currentSettings = s
                    _uiState.update { it.copy(activeSearchEngine = s.defaultSearchEngine) }
                }
            }

            val profileWithProxy = profileDao.getProfileWithProxy(profileId)
            if (profileWithProxy == null) {
                _uiState.update {
                    it.copy(
                        isConnectingProxy = false,
                        proxyBlockedReason = "Browser profile not found."
                    )
                }
                return@launch
            }

            val savedTabs = savedTabDao.getTabsForProfile(profileId)
            val initialTabs = if (savedTabs.isNotEmpty()) {
                savedTabs.map {
                    BrowserTab(
                        id = "tab_${it.id}",
                        title = it.title,
                        url = it.url,
                        isIncognito = it.isIncognito
                    )
                }
            } else {
                listOf(
                    BrowserTab(
                        title = if (profileWithProxy.profile.isIncognitoDefault) "Incognito Tab" else "Home",
                        url = currentSettings.defaultSearchEngine.homeUrl,
                        isIncognito = profileWithProxy.profile.isIncognitoDefault
                    )
                )
            }
            val initialActiveId = savedTabs.firstOrNull { it.isActive }?.let { "tab_${it.id}" }
                ?: initialTabs.first().id

            _uiState.update {
                it.copy(
                    profileWithProxy = profileWithProxy,
                    tabs = initialTabs,
                    activeTabId = initialActiveId,
                    verifiedExitIp = profileWithProxy.proxy?.publicIp ?: profileWithProxy.profile.lastPublicIp
                )
            }

            connectAssignedProxy(profileWithProxy, currentSettings.strictProxyLock, currentSettings.proxyTimeoutMs)
        }
    }

    fun retryProxyConnection() {
        viewModelScope.launch {
            val profileWithProxy = profileDao.getProfileWithProxy(profileId) ?: return@launch
            val currentSettings = settings.value
            connectAssignedProxy(profileWithProxy, currentSettings.strictProxyLock, currentSettings.proxyTimeoutMs)
        }
    }

    private suspend fun connectAssignedProxy(
        profileWithProxy: ProfileWithProxy,
        strictProxyLock: Boolean,
        timeoutMs: Int
    ) {
        val proxy = profileWithProxy.proxy
        if (proxy == null) {
            BrowserEngine.clearProxyRouting(ContextCompat.getMainExecutor(app))
            profileDao.updateStatus(profileId, ProfileStatus.ACTIVE, System.currentTimeMillis())
            _uiState.update {
                it.copy(
                    profileWithProxy = profileWithProxy,
                    proxyTunnelReady = true,
                    isConnectingProxy = false,
                    localBridgePort = null,
                    proxyBlockedReason = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(isConnectingProxy = true, proxyBlockedReason = null, proxyTunnelReady = false)
        }

        runCatching {
            proxyManager.connectProfileProxy(
                profileId = profileId,
                proxyId = proxy.id,
                timeoutMs = timeoutMs,
                verifyConnection = true
            )
        }.onSuccess { tunnel ->
            if (tunnel.isConnected && tunnel.localPort > 0) {
                BrowserEngine.applyProxyRouting(
                    localBridgePort = tunnel.localPort,
                    executor = ContextCompat.getMainExecutor(app),
                    onApplied = {
                        _uiState.update {
                            it.copy(
                                proxyTunnelReady = true,
                                isConnectingProxy = false,
                                localBridgePort = tunnel.localPort,
                                verifiedExitIp = tunnel.publicIp,
                                proxyBlockedReason = null
                            )
                        }
                    },
                    onUnsupported = { msg ->
                        _uiState.update {
                            it.copy(
                                proxyTunnelReady = false,
                                isConnectingProxy = false,
                                localBridgePort = null,
                                proxyBlockedReason = msg
                            )
                        }
                    }
                )
            } else {
                val reason = tunnel.lastError ?: "Unable to connect to SOCKS5 proxy ${proxy.host}:${proxy.port}"
                handleProxyFailure(reason, strictProxyLock)
            }
        }.onFailure { err ->
            handleProxyFailure(err.message ?: "SOCKS5 tunnel error", strictProxyLock)
        }
    }

    private fun handleProxyFailure(reason: String, strictProxyLock: Boolean) {
        if (strictProxyLock) {
            BrowserEngine.applyProxyRouting(
                localBridgePort = 1, // unreachable loopback guard
                executor = ContextCompat.getMainExecutor(app),
                onApplied = {},
                onUnsupported = {}
            )
            _uiState.update {
                it.copy(
                    proxyTunnelReady = false,
                    isConnectingProxy = false,
                    localBridgePort = null,
                    proxyBlockedReason = "SOCKS5 Proxy Failed: $reason\nDirect network fallback is blocked."
                )
            }
        } else {
            BrowserEngine.clearProxyRouting(ContextCompat.getMainExecutor(app))
            _uiState.update {
                it.copy(
                    proxyTunnelReady = true,
                    isConnectingProxy = false,
                    localBridgePort = null,
                    proxyBlockedReason = null,
                    snackbarMessage = "Proxy failed ($reason); Strict Lock is OFF"
                )
            }
        }
    }

    fun verifyLivePublicIp() {
        val proxy = _uiState.value.profileWithProxy?.proxy
        if (proxy == null) {
            _uiState.update { it.copy(snackbarMessage = "Profile uses Direct network (no SOCKS5 proxy assigned)") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(snackbarMessage = "Verifying SOCKS5 exit IP...") }
            when (val res = proxyManager.testProxy(proxy.id, settings.value.proxyTimeoutMs)) {
                is ProxyCheckResult.Success -> {
                    val cc = res.countryCode?.let { " ($it)" }.orEmpty()
                    _uiState.update {
                        it.copy(
                            verifiedExitIp = res.publicIp,
                            snackbarMessage = "Verified Exit IP: ${res.publicIp}$cc • ${res.latencyMs}ms"
                        )
                    }
                }
                is ProxyCheckResult.Failure -> {
                    _uiState.update { it.copy(snackbarMessage = "IP Check Failed: ${res.reason}") }
                }
            }
        }
    }

    fun addTab(isIncognito: Boolean, initialUrl: String? = null) {
        val targetUrl = initialUrl ?: _uiState.value.activeSearchEngine.homeUrl
        val tab = BrowserTab(
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            url = targetUrl,
            isIncognito = isIncognito
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabId = tab.id
            )
        }
        persistTabs()
    }

    fun selectTab(tabId: String) {
        _uiState.update { it.copy(activeTabId = tabId) }
        persistTabs()
    }

    fun closeTab(tabId: String) {
        val closing = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val remaining = _uiState.value.tabs.filterNot { it.id == tabId }
        if (closing?.isIncognito == true && remaining.none { it.isIncognito }) {
            _uiState.value.profileWithProxy?.profile?.storageKey?.let {
                BrowserEngine.deleteIncognitoEphemeralProfile(it)
            }
        }

        if (remaining.isEmpty()) {
            val replacement = BrowserTab(
                title = "Home",
                url = _uiState.value.activeSearchEngine.homeUrl,
                isIncognito = false
            )
            _uiState.update { it.copy(tabs = listOf(replacement), activeTabId = replacement.id) }
        } else {
            val nextActive = if (_uiState.value.activeTabId == tabId) remaining.last().id else _uiState.value.activeTabId
            _uiState.update { it.copy(tabs = remaining, activeTabId = nextActive) }
        }
        persistTabs()
    }

    fun updateTabState(
        tabId: String,
        url: String? = null,
        title: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        _uiState.update { state ->
            val updated = state.tabs.map { tab ->
                if (tab.id != tabId) tab
                else tab.copy(
                    url = url ?: tab.url,
                    title = title?.takeIf { it.isNotBlank() } ?: tab.title,
                    isLoading = isLoading ?: tab.isLoading,
                    progress = progress ?: tab.progress,
                    canGoBack = canGoBack ?: tab.canGoBack,
                    canGoForward = canGoForward ?: tab.canGoForward
                )
            }
            state.copy(tabs = updated)
        }
    }

    fun recordPageVisit(tabId: String, title: String, url: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.isIncognito || url.startsWith("about:")) return
        viewModelScope.launch {
            historyDao.insert(
                HistoryEntity(
                    profileId = profileId,
                    title = title.ifBlank { url },
                    url = url
                )
            )
            persistTabs()
        }
    }

    fun toggleBookmark(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        viewModelScope.launch {
            val existing = bookmarks.value.firstOrNull { it.url == url }
            if (existing != null) {
                bookmarkDao.deleteById(existing.id)
                _uiState.update { it.copy(snackbarMessage = "Bookmark removed") }
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(
                        profileId = profileId,
                        title = title.ifBlank { url },
                        url = url
                    )
                )
                _uiState.update { it.copy(snackbarMessage = "Bookmarked in this profile") }
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkDao.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearForProfile(profileId)
            _uiState.update { it.copy(snackbarMessage = "Cleared profile browsing history") }
        }
    }

    fun toggleDesktopMode() {
        _uiState.update { it.copy(desktopMode = !it.desktopMode) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsStore.setSearchEngine(engine) }
        _uiState.update { it.copy(activeSearchEngine = engine) }
    }

    fun startProxiedDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val profile = _uiState.value.profileWithProxy?.profile ?: return
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val bridgePort = _uiState.value.localBridgePort

        if (_uiState.value.profileWithProxy?.proxy != null && bridgePort == null) {
            _uiState.update { it.copy(snackbarMessage = "Download blocked: SOCKS5 proxy tunnel is not active") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val downloadsDir = profileManager.getProfileDownloadsDirectory(profile.storageKey)
            val targetFile = File(downloadsDir, "${System.currentTimeMillis()}_$fileName")

            val rowId = downloadDao.insert(
                DownloadEntity(
                    profileId = profileId,
                    fileName = fileName,
                    url = url,
                    mimeType = mimeType ?: "application/octet-stream",
                    status = "DOWNLOADING",
                    localPath = targetFile.absolutePath
                )
            )
            _uiState.update { it.copy(snackbarMessage = "Downloading $fileName...") }

            runCatching {
                val client = BrowserEngine.createDownloadClient(bridgePort)
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("Empty response")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var sum = 0L
                            while (true) {
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                sum += read
                            }
                            downloadDao.update(
                                DownloadEntity(
                                    id = rowId,
                                    profileId = profileId,
                                    fileName = fileName,
                                    url = url,
                                    mimeType = mimeType ?: "application/octet-stream",
                                    bytesDownloaded = sum,
                                    totalBytes = if (total > 0) total else sum,
                                    status = "COMPLETED",
                                    localPath = targetFile.absolutePath
                                )
                            )
                        }
                    }
                }
            }.onSuccess {
                _uiState.update { it.copy(snackbarMessage = "Saved $fileName to profile downloads") }
            }.onFailure { err ->
                downloadDao.update(
                    DownloadEntity(
                        id = rowId,
                        profileId = profileId,
                        fileName = fileName,
                        url = url,
                        mimeType = mimeType ?: "application/octet-stream",
                        status = "FAILED: ${err.message ?: "Error"}",
                        localPath = targetFile.absolutePath
                    )
                )
                _uiState.update { it.copy(snackbarMessage = "Download failed: ${err.message}") }
            }
        }
    }

    fun clearProfileData() {
        viewModelScope.launch {
            profileManager.clearProfileBrowserStorage(profileId)
            _uiState.update { it.copy(snackbarMessage = "Wiped cookies, cache, and history for this profile") }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun persistTabs() {
        val snapshot = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            val persistentTabs = snapshot.tabs
                .filterNot { it.isIncognito }
                .mapIndexed { idx, tab ->
                    SavedTabEntity(
                        profileId = profileId,
                        tabOrder = idx,
                        title = tab.title,
                        url = tab.url,
                        isActive = tab.id == snapshot.activeTabId,
                        isIncognito = false
                    )
                }
            savedTabDao.replaceTabsForProfile(profileId, persistentTabs)
        }
    }

    fun shutdownSession() {
        persistTabs()
        _uiState.value.profileWithProxy?.profile?.storageKey?.let {
            BrowserEngine.deleteIncognitoEphemeralProfile(it)
        }
        BrowserEngine.clearProxyRouting(ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            proxyManager.disconnectProfileProxy(profileId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        shutdownSession()
    }
}

class BrowserActivity : ComponentActivity() {

    private var sessionViewModel: BrowserSessionViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)

        setContent {
            NexoBrowserTheme {
                val vm: BrowserSessionViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BrowserSessionViewModel(application, profileId) as T
                        }
                    }
                )
                sessionViewModel = vm

                BrowserScreen(
                    viewModel = vm,
                    onExitBrowser = {
                        vm.shutdownSession()
                        finish()
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sessionViewModel?.persistTabs()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserSessionViewModel,
    onExitBrowser: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val webViews = remember { mutableStateMapOf<String, WebView>() }
    val activeTab = state.activeTab
    val activeWebView = activeTab?.let { webViews[it.id] }

    var addressInput by remember(activeTab?.id) { mutableStateOf(activeTab?.url.orEmpty()) }
    var isEditingAddress by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf(BrowserSheet.NONE) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(activeTab?.url, isEditingAddress) {
        if (!isEditingAddress && activeTab != null) {
            addressInput = activeTab.url
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Clean up removed WebViews
    LaunchedEffect(state.tabs) {
        val liveIds = state.tabs.map { it.id }.toSet()
        val removedIds = webViews.keys.filter { it !in liveIds }
        removedIds.forEach { id ->
            webViews.remove(id)?.destroy()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    BackHandler {
        when {
            activeSheet != BrowserSheet.NONE -> activeSheet = BrowserSheet.NONE
            activeWebView?.canGoBack() == true -> activeWebView.goBack()
            else -> onExitBrowser()
        }
    }

    val isBookmarked = activeTab != null && bookmarks.any { it.url == activeTab.url }
    val profile = state.profileWithProxy?.profile
    val proxy = state.profileWithProxy?.proxy

    Scaffold(
        containerColor = NexoColors.ObsidianBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexoColors.DeepSlate)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Telemetry Strip: Profile # + Name + SOCKS5 Shield / Exit IP + Close Profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (activeTab?.isIncognito == true) NexoColors.IncognitoPurple.copy(alpha = 0.25f) else NexoColors.ElectricBlueSoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (activeTab?.isIncognito == true) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = "Incognito", tint = NexoColors.IncognitoPurple, modifier = Modifier.size(13.dp))
                                }
                                Text(
                                    text = "#${profile?.profileNumber?.toString()?.padStart(3, '0') ?: "---"} ${profile?.name ?: ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                state.proxyBlockedReason != null -> NexoColors.CoralSoft
                                proxy != null && state.proxyTunnelReady -> NexoColors.EmeraldSoft
                                proxy != null -> NexoColors.BlazeOrangeSoft
                                else -> NexoColors.CardSurfaceHigh
                            },
                            modifier = Modifier.clickable { viewModel.verifyLivePublicIp() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.proxyBlockedReason != null) Icons.Default.GppBad else Icons.Default.Shield,
                                    contentDescription = "Proxy Shield",
                                    tint = if (proxy != null && state.proxyTunnelReady) NexoColors.EmeraldOk else NexoColors.BlazeOrange,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = when {
                                        state.isConnectingProxy -> "Connecting SOCKS5..."
                                        state.proxyBlockedReason != null -> "BLOCKED"
                                        proxy != null -> state.verifiedExitIp ?: "${proxy.host}:${proxy.port}"
                                        else -> "Direct"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexoColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onExitBrowser,
                        modifier = Modifier.testTag("close_profile_button")
                    ) {
                        Text("Close", color = NexoColors.BlazeOrange, style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Chrome-Style Omnibox Address Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = {
                            addressInput = it
                            isEditingAddress = true
                        },
                        singleLine = true,
                        placeholder = { Text("Search ${state.activeSearchEngine.label} or type URL", color = NexoColors.TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (activeTab?.url?.startsWith("https://") == true) Icons.Default.Lock else Icons.Default.Public,
                                contentDescription = "Connection security",
                                tint = if (activeTab?.url?.startsWith("https://") == true) NexoColors.EmeraldOk else NexoColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isEditingAddress && addressInput.isNotEmpty()) {
                                IconButton(onClick = { addressInput = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear URL", tint = NexoColors.TextSecondary)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val resolved = BrowserEngine.formatAddressOrSearch(addressInput, state.activeSearchEngine)
                                isEditingAddress = false
                                focusManager.clearFocus()
                                activeWebView?.loadUrl(resolved)
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexoColors.ElectricBlue,
                            unfocusedBorderColor = NexoColors.BorderSubtle,
                            focusedContainerColor = NexoColors.ObsidianBlack,
                            unfocusedContainerColor = NexoColors.ObsidianBlack,
                            focusedTextColor = NexoColors.TextPrimary,
                            unfocusedTextColor = NexoColors.TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_address_bar")
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexoColors.CardSurfaceHigh,
                        border = BorderStroke(1.dp, NexoColors.BorderSubtle),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { activeSheet = BrowserSheet.TABS }
                            .testTag("tabs_count_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = state.tabs.size.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = NexoColors.ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (activeTab?.isLoading == true) {
                    LinearProgressIndicator(
                        progress = { (activeTab.progress.coerceIn(5, 100)) / 100f },
                        color = NexoColors.ElectricBlue,
                        trackColor = NexoColors.CardSurfaceHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = NexoColors.DeepSlate,
                border = BorderStroke(1.dp, NexoColors.BorderSubtle.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { activeWebView?.goBack() },
                        enabled = activeTab?.canGoBack == true,
                        modifier = Modifier.size(48.dp).testTag("browser_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (activeTab?.canGoBack == true) NexoColors.TextPrimary else NexoColors.TextMuted
                        )
                    }

                    IconButton(
                        onClick = { activeWebView?.goForward() },
                        enabled = activeTab?.canGoForward == true,
                        modifier = Modifier.size(48.dp).testTag("browser_forward_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (activeTab?.canGoForward == true) NexoColors.TextPrimary else NexoColors.TextMuted
                        )
                    }

                    IconButton(
                        onClick = { activeWebView?.loadUrl(state.activeSearchEngine.homeUrl) },
                        modifier = Modifier.size(48.dp).testTag("browser_home_button")
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = NexoColors.TextPrimary)
                    }

                    IconButton(
                        onClick = {
                            if (activeTab?.isLoading == true) activeWebView?.stopLoading()
                            else activeWebView?.reload()
                        },
                        modifier = Modifier.size(48.dp).testTag("browser_refresh_button")
                    ) {
                        Icon(
                            if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (activeTab?.isLoading == true) "Stop" else "Refresh",
                            tint = NexoColors.ElectricBlue
                        )
                    }

                    IconButton(
                        onClick = {
                            if (activeTab != null) {
                                viewModel.toggleBookmark(activeTab.title, activeTab.url)
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("browser_bookmark_button")
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark page",
                            tint = if (isBookmarked) NexoColors.BlazeOrange else NexoColors.TextPrimary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(48.dp).testTag("browser_menu_button")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Browser menu", tint = NexoColors.TextPrimary)
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.addTab(isIncognito = false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Incognito Tab") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = NexoColors.IncognitoPurple) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.addTab(isIncognito = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks (${bookmarks.size})") },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = NexoColors.BlazeOrange) },
                                onClick = {
                                    showOverflowMenu = false
                                    activeSheet = BrowserSheet.BOOKMARKS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("History (${history.size})") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    activeSheet = BrowserSheet.HISTORY
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Downloads (${downloads.size})") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    activeSheet = BrowserSheet.DOWNLOADS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.desktopMode) "Switch to Mobile View" else "Desktop Site") },
                                leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleDesktopMode()
                                    val ua = if (!state.desktopMode) UserAgentPreset.DESKTOP_CHROME else UserAgentPreset.MOBILE_CHROME
                                    activeWebView?.settings?.userAgentString = ua.userAgent
                                    activeWebView?.reload()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search Engine (${state.activeSearchEngine.label})") },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    activeSheet = BrowserSheet.SEARCH_ENGINE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Verify SOCKS5 Exit IP") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = NexoColors.EmeraldOk) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.verifyLivePublicIp()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wipe Profile Site Data") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = NexoColors.CoralError) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.clearProfileData()
                                    activeWebView?.reload()
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(NexoColors.ObsidianBlack)
        ) {
            when {
                state.isConnectingProxy -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = NexoColors.ElectricBlue)
                        Text(
                            "Authenticating SOCKS5 Tunnel...",
                            style = MaterialTheme.typography.titleLarge,
                            color = NexoColors.TextPrimary
                        )
                        Text(
                            "Verifying proxy credentials and exit IP before loading any web content.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexoColors.TextSecondary
                        )
                    }
                }

                state.proxyBlockedReason != null -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = NexoColors.CardSurface,
                        border = BorderStroke(1.dp, NexoColors.CoralError),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.GppBad,
                                contentDescription = "Blocked",
                                tint = NexoColors.CoralError,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Direct Connection Blocked",
                                style = MaterialTheme.typography.headlineSmall,
                                color = NexoColors.CoralError
                            )
                            Text(
                                state.proxyBlockedReason!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexoColors.TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.retryProxyConnection() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue)
                                ) {
                                    Text("Retry SOCKS5")
                                }
                                OutlinedButton(onClick = onExitBrowser) {
                                    Text("Back to Dashboard", color = NexoColors.TextPrimary)
                                }
                            }
                        }
                    }
                }

                state.proxyTunnelReady && activeTab != null && profile != null -> {
                    val uaPreset = if (state.desktopMode) {
                        UserAgentPreset.DESKTOP_CHROME
                    } else {
                        runCatching { UserAgentPreset.valueOf(profile.userAgentPreset) }
                            .getOrDefault(UserAgentPreset.MOBILE_CHROME)
                    }

                    key(activeTab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                webViews.getOrPut(activeTab.id) {
                                    WebView(ctx).apply {
                                        BrowserEngine.configureWebViewForProfile(
                                            webView = this,
                                            profileStorageKey = profile.storageKey,
                                            isIncognito = activeTab.isIncognito,
                                            userAgentPreset = uaPreset,
                                            forceDarkWeb = settings.forceDarkWebContent
                                        )

                                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                            viewModel.startProxiedDownload(url, contentDisposition, mimeType)
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                viewModel.updateTabState(
                                                    tabId = activeTab.id,
                                                    progress = newProgress,
                                                    isLoading = newProgress < 100
                                                )
                                            }

                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                viewModel.updateTabState(tabId = activeTab.id, title = title)
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                if (settings.webrtcProtection) {
                                                    view?.evaluateJavascript(BrowserEngine.WEBRTC_GUARD_JS, null)
                                                }
                                                viewModel.updateTabState(
                                                    tabId = activeTab.id,
                                                    url = url,
                                                    isLoading = true,
                                                    canGoBack = view?.canGoBack() ?: false,
                                                    canGoForward = view?.canGoForward() ?: false
                                                )
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                val finalUrl = url ?: activeTab.url
                                                val pageTitle = view?.title ?: finalUrl
                                                viewModel.updateTabState(
                                                    tabId = activeTab.id,
                                                    url = finalUrl,
                                                    title = pageTitle,
                                                    isLoading = false,
                                                    progress = 100,
                                                    canGoBack = view?.canGoBack() ?: false,
                                                    canGoForward = view?.canGoForward() ?: false
                                                )
                                                viewModel.recordPageVisit(activeTab.id, pageTitle, finalUrl)
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?
                                            ) {
                                                if (request?.isForMainFrame == true) {
                                                    viewModel.updateTabState(tabId = activeTab.id, isLoading = false)
                                                }
                                            }
                                        }

                                        loadUrl(activeTab.url)
                                    }
                                }
                            },
                            update = { wv ->
                                if (wv.settings.userAgentString != uaPreset.userAgent) {
                                    wv.settings.userAgentString = uaPreset.userAgent
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (activeSheet != BrowserSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = BrowserSheet.NONE },
            containerColor = NexoColors.CardSurfaceHigh
        ) {
            when (activeSheet) {
                BrowserSheet.TABS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tabs (${state.tabs.size})", style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    viewModel.addTab(isIncognito = true)
                                    activeSheet = BrowserSheet.NONE
                                }) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = NexoColors.IncognitoPurple)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Incognito", color = NexoColors.IncognitoPurple)
                                }
                                Button(
                                    onClick = {
                                        viewModel.addTab(isIncognito = false)
                                        activeSheet = BrowserSheet.NONE
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexoColors.ElectricBlue)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New Tab")
                                }
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(state.tabs, key = { it.id }) { tab ->
                                val isActive = tab.id == state.activeTabId
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isActive) NexoColors.ElectricBlueSoft else NexoColors.CardSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            tab.isIncognito -> NexoColors.IncognitoPurple
                                            isActive -> NexoColors.ElectricBlue
                                            else -> NexoColors.BorderSubtle
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectTab(tab.id)
                                            activeSheet = BrowserSheet.NONE
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (tab.isIncognito) "[Incognito] ${tab.title}" else tab.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = NexoColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = tab.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NexoColors.TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { viewModel.closeTab(tab.id) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close tab", tint = NexoColors.TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BrowserSheet.BOOKMARKS -> {
                    SheetListContainer(
                        title = "Profile Bookmarks",
                        emptyMessage = "No bookmarks saved in this profile yet."
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            SheetLinkItem(
                                title = bm.title,
                                subtitle = bm.url,
                                onOpen = {
                                    activeWebView?.loadUrl(bm.url)
                                    activeSheet = BrowserSheet.NONE
                                },
                                onDelete = { viewModel.deleteBookmark(bm.id) }
                            )
                        }
                    }
                }

                BrowserSheet.HISTORY -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Profile History", style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
                            TextButton(onClick = { viewModel.clearHistory() }) {
                                Text("Clear", color = NexoColors.CoralError)
                            }
                        }
                        if (history.isEmpty()) {
                            Text(
                                "No browsing history in this profile.",
                                color = NexoColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(history, key = { it.id }) { item ->
                                    val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.visitedAt))
                                    SheetLinkItem(
                                        title = item.title,
                                        subtitle = "${item.url} • $timeStr",
                                        onOpen = {
                                            activeWebView?.loadUrl(item.url)
                                            activeSheet = BrowserSheet.NONE
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                BrowserSheet.DOWNLOADS -> {
                    SheetListContainer(
                        title = "Profile Downloads",
                        emptyMessage = "No downloaded files in this profile."
                    ) {
                        items(downloads, key = { it.id }) { dl ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = NexoColors.CardSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(dl.fileName, style = MaterialTheme.typography.titleMedium, color = NexoColors.TextPrimary)
                                    Text(
                                        "${dl.status} • ${dl.bytesDownloaded / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (dl.status == "COMPLETED") NexoColors.EmeraldOk else NexoColors.BlazeOrange
                                    )
                                    Text(
                                        dl.localPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexoColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                BrowserSheet.SEARCH_ENGINE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Search Engine", style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
                        SearchEngine.entries.forEach { engine ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (engine == state.activeSearchEngine) NexoColors.ElectricBlueSoft else NexoColors.CardSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSearchEngine(engine)
                                        activeSheet = BrowserSheet.NONE
                                    }
                            ) {
                                Text(
                                    text = engine.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NexoColors.TextPrimary,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                BrowserSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun SheetListContainer(
    title: String,
    emptyMessage: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = NexoColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            content = content
        )
        Text(emptyMessage, style = MaterialTheme.typography.labelSmall, color = Color.Transparent)
    }
}

@Composable
private fun SheetLinkItem(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NexoColors.CardSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NexoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = NexoColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = NexoColors.TextMuted)
                }
            }
        }
    }
}
