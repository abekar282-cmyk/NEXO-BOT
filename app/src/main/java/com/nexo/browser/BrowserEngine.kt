package com.nexo.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

data class EngineCapabilities(
    val engineName: String,
    val webViewVersion: String,
    val supportsMultiProfile: Boolean,
    val supportsProxyOverride: Boolean,
    val supportsAlgorithmicDarkening: Boolean
)

/**
 * Evaluates and configures NEXO BROWSER's underlying Chromium-based WebView + AndroidX WebKit
 * engine with genuine per-profile storage isolation and strict local SOCKS5 bridge routing.
 */
object BrowserEngine {

    const val WEBRTC_GUARD_JS = """
        (function() {
            try {
                if (typeof window !== 'undefined') {
                    window.RTCPeerConnection = undefined;
                    window.webkitRTCPeerConnection = undefined;
                    window.mozRTCPeerConnection = undefined;
                    if (navigator && navigator.mediaDevices) {
                        navigator.mediaDevices.getUserMedia = function() {
                            return Promise.reject(new Error('WebRTC disabled by NEXO Proxy Shield'));
                        };
                    }
                }
            } catch (e) {}
        })();
    """

    fun inspectCapabilities(context: Context): EngineCapabilities {
        val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
        val version = pkg?.versionName ?: "System WebView"
        return EngineCapabilities(
            engineName = "Chromium WebView + AndroidX WebKit + Local RFC1928 SOCKS5 Bridge",
            webViewVersion = version,
            supportsMultiProfile = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE),
            supportsProxyOverride = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE),
            supportsAlgorithmicDarkening = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
        )
    }

    /**
     * Binds a WebView instance to a dedicated isolated Profile (separate cookies, cache,
     * LocalStorage/IndexedDB, and Service Workers).
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebViewForProfile(
        webView: WebView,
        profileStorageKey: String,
        isIncognito: Boolean,
        userAgentPreset: UserAgentPreset,
        forceDarkWeb: Boolean
    ): Profile? {
        val targetProfileKey = if (isIncognito) {
            "${profileStorageKey}_incognito_ephemeral"
        } else {
            profileStorageKey
        }

        var webKitProfile: Profile? = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            webKitProfile = ProfileStore.getInstance().getOrCreateProfile(targetProfileKey)
            WebViewCompat.setProfile(webView, targetProfileKey)
            webKitProfile.cookieManager.setAcceptCookie(true)
            webKitProfile.cookieManager.setAcceptThirdPartyCookies(webView, true)
        } else {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !isIncognito
            databaseEnabled = !isIncognito
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            userAgentString = userAgentPreset.userAgent
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, forceDarkWeb)
        }

        return webKitProfile
    }

    fun deleteIncognitoEphemeralProfile(profileStorageKey: String) {
        val ephemeralKey = "${profileStorageKey}_incognito_ephemeral"
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getProfile(ephemeralKey)?.let {
                    it.cookieManager.removeAllCookies(null)
                    it.webStorage.deleteAllData()
                }
                ProfileStore.getInstance().deleteProfile(ephemeralKey)
            }
        }
    }

    /**
     * Routes all Chromium WebView network requests exclusively through the local HTTP-to-SOCKS5
     * bridge (127.0.0.1:<localPort>). No DIRECT rule is added, preventing silent fallback.
     */
    fun applyProxyRouting(
        localBridgePort: Int?,
        executor: Executor,
        onApplied: () -> Unit,
        onUnsupported: (String) -> Unit
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (localBridgePort != null) {
                onUnsupported("This device's WebView does not support ProxyController override; blocking direct fallback.")
            } else {
                onApplied()
            }
            return
        }

        val controller = ProxyController.getInstance()
        if (localBridgePort != null && localBridgePort > 0) {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("http://127.0.0.1:$localBridgePort")
                .build()
            controller.setProxyOverride(proxyConfig, executor, onApplied)
        } else {
            controller.clearProxyOverride(executor, onApplied)
        }
    }

    fun clearProxyRouting(executor: Executor, onCleared: () -> Unit = {}) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(executor, onCleared)
        } else {
            onCleared()
        }
    }

    /**
     * Builds an OkHttpClient for profile-scoped file downloads routed strictly through
     * the profile's local SOCKS5 bridge when a proxy is active.
     */
    fun createDownloadClient(localBridgePort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)

        if (localBridgePort != null && localBridgePort > 0) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", localBridgePort)))
        }
        return builder.build()
    }

    fun formatAddressOrSearch(input: String, searchEngine: SearchEngine): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return searchEngine.homeUrl
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || trimmed.startsWith("localhost", ignoreCase = true))
        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            searchEngine.searchUrlTemplate.replace("%s", encoded)
        }
    }

    val architectureEvaluationNotes: List<Pair<String, String>> = listOf(
        "Selected Engine: Chromium WebView + AndroidX WebKit" to
            "Uses WebViewFeature.MULTI_PROFILE (ProfileStore) for native Chromium storage isolation (cookies, cache, LocalStorage, ServiceWorkers) across 100 profiles inside a lightweight APK (~12 MB instead of ~90 MB+ with bundled GeckoView).",
        "SOCKS5 Auth Bridge Architecture" to
            "Android WebView's ProxyController cannot natively inject RFC 1929 SOCKS5 username/password credentials. NEXO BROWSER solves this cleanly via an in-process 127.0.0.1 HTTP-to-SOCKS5 bridge (LocalSocks5Bridge) paired with ProxyController with zero DIRECT fallback rules.",
        "Known Engine Limitations" to
            "1) Devices running an outdated WebView APK older than Chromium M119 do not support ProfileStore multi-profile isolation. 2) WebView does not expose a native C++ flag to disable UDP STUN/WebRTC at the socket level like GeckoView's media.peerconnection.enabled; NEXO applies a JS RTCPeerConnection guard on document start, though strict anonymity users should verify WebRTC status."
    )
}
