# NEXO BROWSER

**NEXO BROWSER** is a dark-themed, multi-profile Android browser (`com.nexo.browser`) designed for isolated browsing environments and authenticated SOCKS5 proxy routing.

---

## Core Architecture & Features

### 1. 100 Isolated Browser Profiles
- **Genuine Storage Isolation**: Uses `androidx.webkit` `ProfileStore` (`WebViewFeature.MULTI_PROFILE`) to assign each profile an independent Chromium storage container (`nexo_p_<num>_<uuid>`), isolating cookies, cache, LocalStorage/IndexedDB, and service workers.
- **Physical Sandbox & Scoped Data**: Each profile also gets its own filesystem directory (`filesDir/nexo_profiles/<storageKey>/`) for downloads and profile-scoped Room tables (`bookmarks`, `history`, `downloads`, `saved_tabs`).
- **Profile Management**: Create up to 100 profiles, batch-provision 100 profiles in one tap, clone, rename, delete, wipe profile storage, search, and sort.

### 2. Real Authenticated SOCKS5 Networking (Fail-Closed)
- **RFC 1928 & RFC 1929 Implementation** (`Socks5Client.kt`): Native Kotlin SOCKS5 handshake, username/password subnegotiation, and remote domain resolution (`ATYP=0x03`) to prevent local DNS leaks.
- **In-App Local HTTP-to-SOCKS5 Bridge** (`LocalSocks5Bridge`): Routes all Android WebView traffic (`ProxyController.setProxyOverride`) and OkHttp file downloads through `127.0.0.1:<port>` into the assigned SOCKS5 proxy.
- **No Direct Fallback**: `ProxyConfig` sets no `DIRECT` rule. If the upstream SOCKS5 proxy fails or refuses credentials, traffic is blocked (`502 Bad Gateway` + Proxy Shield screen) instead of leaking your real IP.
- **Encrypted Credentials**: Proxy passwords are encrypted at rest with **AES-256-GCM** backed by **AndroidKeyStore** (`SecurityManager.kt`).

### 3. Bulk TXT Proxy Import
- Zero-permission Android Storage Access Framework (SAF) `.txt` picker or text paste.
- Supports `socks5://username:password@ip:port` and `ip:port:username:password`.
- Validates format, detects duplicates, imports up to 100 proxies, auto-assigns 1-to-1 to profiles, and verifies live exit Public IP & latency.

### 4. Browser Engine Evaluation & Honest Limitations
- **Selected Engine**: Android Chromium WebView + AndroidX WebKit (`ProfileStore` + `ProxyController`) + Local RFC 1928/1929 SOCKS5 Bridge.
- **Why not GeckoView?**: Bundling GeckoView adds ~80–100 MB of native libraries per ABI and complicates free cloud builds and per-profile dynamic SOCKS5 authentication.
- **Known WebView Limitations**:
  1. Native `ProfileStore` multi-profile isolation requires Android System WebView M119+ (standard on Android 13–15 devices with Play updates).
  2. WebView does not expose a native socket-level WebRTC ICE toggle like GeckoView's `media.peerconnection.enabled`; NEXO injects a JavaScript `RTCPeerConnection` guard on page start (`WEBRTC_GUARD_JS`).

---

## Android Phone Only: GitHub Upload, Build & APK Download Guide

You do **not** need a computer to build or install **NEXO BROWSER**.

### Step 1: Upload Project to GitHub (From Your Android Phone)
1. Install or open the **GitHub** app (or open **github.com** in your phone browser in Desktop Site mode) and sign in.
2. Tap **+** -> **New repository**, name it `NEXO-BROWSER`, choose **Private** or **Public**, and tap **Create repository**.
3. If using **Termux** or **Acode / Git client** on Android, or GitHub Web **Upload files**:
   - Upload all files from this project folder into the root of your repository, making sure `.github/workflows/android.yml`, `gradlew`, `gradlew.bat`, `gradle/`, `app/`, `build.gradle.kts`, and `settings.gradle.kts` are all included.
   - Make sure you commit to the `main` branch.

### Step 2: Automatic Cloud APK Build (Free GitHub Actions)
1. Open your `NEXO-BROWSER` repository on **github.com**.
2. Tap the **Actions** tab at the top.
3. You will see **NEXO BROWSER Android CI** running automatically (or tap **Run workflow** manually at any time).
4. GitHub's free Ubuntu runner will automatically:
   - Set up JDK 21, Android SDK, and Gradle
   - Generate an ephemeral `debug.keystore` (never committed)
   - Run all unit & Robolectric tests (`./gradlew testDebugUnitTest`)
   - Build the Debug APK (`./gradlew assembleDebug`)
   - Verify `app/build/outputs/apk/debug/app-debug.apk`
   - Upload the APK as an artifact.

### Step 3: Download & Install the APK on Your Android Phone
1. Wait until the workflow run shows a **green checkmark** (usually 3–5 minutes).
2. Tap on the completed workflow run and scroll down to **Artifacts**.
3. Tap **nexo-browser-debug-apk** to download the `.zip` file to your phone.
4. Open your Android **Files** app, extract the downloaded `.zip`, and tap `app-debug.apk` to install **NEXO BROWSER** (allow "Install unknown apps" for your Files app if prompted).
