package com.nexo.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProfileManager(
    private val context: Context,
    private val profileDao: ProfileDao,
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val savedTabDao: SavedTabDao,
    private val proxyManager: ProxyManager
) {
    val profilesFlow: Flow<List<ProfileWithProxy>> = profileDao.observeProfilesWithProxy()

    fun observeProfile(profileId: Long): Flow<ProfileWithProxy?> =
        profileDao.observeProfileWithProxy(profileId)

    fun getProfileDirectory(storageKey: String): File {
        val dir = File(File(context.filesDir, "nexo_profiles"), storageKey)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        File(dir, "downloads").mkdirs()
        File(dir, "cache").mkdirs()
        return dir
    }

    fun getProfileDownloadsDirectory(storageKey: String): File {
        return File(getProfileDirectory(storageKey), "downloads").apply { mkdirs() }
    }

    suspend fun seedInitialProfilesIfEmpty(initialCount: Int = 5) = withContext(Dispatchers.IO) {
        if (profileDao.getProfileCount() == 0) {
            batchCreateProfilesUpTo(initialCount.coerceIn(1, MAX_PROFILES))
        }
    }

    suspend fun createProfile(
        name: String,
        proxyId: Long? = null,
        userAgentPreset: UserAgentPreset = UserAgentPreset.MOBILE_CHROME,
        isIncognitoDefault: Boolean = false,
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val existing = profileDao.getAllProfiles()
        require(existing.size < MAX_PROFILES) {
            "Maximum limit of $MAX_PROFILES isolated profiles reached"
        }

        val usedNumbers = existing.map { it.profileNumber }.toSet()
        val nextNumber = (1..MAX_PROFILES).firstOrNull { it !in usedNumbers }
            ?: throw IllegalStateException("No available profile slots (max $MAX_PROFILES)")

        val storageKey = generateStorageKey(nextNumber)
        getProfileDirectory(storageKey)

        val finalName = name.trim().ifBlank { "Profile ${nextNumber.toString().padStart(2, '0')}" }
        profileDao.insertProfile(
            BrowserProfileEntity(
                profileNumber = nextNumber,
                name = finalName,
                storageKey = storageKey,
                proxyId = proxyId,
                userAgentPreset = userAgentPreset.name,
                isIncognitoDefault = isIncognitoDefault,
                notes = notes.trim()
            )
        )
    }

    suspend fun batchCreateProfilesUpTo(targetCount: Int = MAX_PROFILES): Int =
        withContext(Dispatchers.IO) {
            val cappedTarget = targetCount.coerceIn(1, MAX_PROFILES)
            val existing = profileDao.getAllProfiles()
            if (existing.size >= cappedTarget) return@withContext 0

            val usedNumbers = existing.map { it.profileNumber }.toMutableSet()
            val toInsert = mutableListOf<BrowserProfileEntity>()
            val needed = cappedTarget - existing.size

            for (num in 1..MAX_PROFILES) {
                if (toInsert.size >= needed) break
                if (num !in usedNumbers) {
                    usedNumbers += num
                    val key = generateStorageKey(num)
                    getProfileDirectory(key)
                    toInsert += BrowserProfileEntity(
                        profileNumber = num,
                        name = "Profile ${num.toString().padStart(3, '0')}",
                        storageKey = key
                    )
                }
            }

            if (toInsert.isNotEmpty()) {
                profileDao.insertProfiles(toInsert)
            }
            toInsert.size
        }

    suspend fun cloneProfile(sourceProfileId: Long): Long = withContext(Dispatchers.IO) {
        val source = profileDao.getProfileById(sourceProfileId)
            ?: throw IllegalArgumentException("Profile #$sourceProfileId not found")

        val existing = profileDao.getAllProfiles()
        require(existing.size < MAX_PROFILES) {
            "Maximum limit of $MAX_PROFILES isolated profiles reached"
        }

        val usedNumbers = existing.map { it.profileNumber }.toSet()
        val nextNumber = (1..MAX_PROFILES).firstOrNull { it !in usedNumbers }
            ?: throw IllegalStateException("No available profile slots")

        val newStorageKey = generateStorageKey(nextNumber)
        getProfileDirectory(newStorageKey)

        val clonedId = profileDao.insertProfile(
            source.copy(
                id = 0,
                profileNumber = nextNumber,
                name = "${source.name} (Clone)",
                storageKey = newStorageKey,
                status = ProfileStatus.IDLE,
                lastOpenedAt = null,
                createdAt = System.currentTimeMillis()
            )
        )

        // Clone bookmarks into the new profile while keeping cookies, cache, and history isolated
        val sourceBookmarks = bookmarkDao.getForProfile(sourceProfileId)
        if (sourceBookmarks.isNotEmpty()) {
            bookmarkDao.insertAll(
                sourceBookmarks.map {
                    BookmarkEntity(
                        profileId = clonedId,
                        title = it.title,
                        url = it.url
                    )
                }
            )
        }
        clonedId
    }

    suspend fun renameProfile(profileId: Long, newName: String) = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        require(clean.isNotEmpty()) { "Profile name cannot be empty" }
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        profileDao.updateProfile(profile.copy(name = clean))
    }

    suspend fun updateProfileConfig(
        profileId: Long,
        name: String,
        proxyId: Long?,
        userAgentPreset: UserAgentPreset,
        isIncognitoDefault: Boolean,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        val cleanName = name.trim().ifBlank { profile.name }
        if (profile.proxyId != proxyId) {
            proxyManager.disconnectProfileProxy(profileId)
        }
        profileDao.updateProfile(
            profile.copy(
                name = cleanName,
                proxyId = proxyId,
                userAgentPreset = userAgentPreset.name,
                isIncognitoDefault = isIncognitoDefault,
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteProfile(profileId: Long) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        proxyManager.disconnectProfileProxy(profileId)

        // Delete isolated Chromium WebView profile storage if supported
        runOnMainThread {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    ProfileStore.getInstance().deleteProfile(profile.storageKey)
                }
            }
        }

        // Delete physical sandbox folder
        val dir = File(File(context.filesDir, "nexo_profiles"), profile.storageKey)
        if (dir.exists()) {
            dir.deleteRecursively()
        }

        profileDao.deleteProfileById(profileId)
    }

    suspend fun clearProfileBrowserStorage(profileId: Long) = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(profileId) ?: return@withContext
        runOnMainThread {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val webProfile = ProfileStore.getInstance().getProfile(profile.storageKey)
                    webProfile?.cookieManager?.removeAllCookies(null)
                    webProfile?.cookieManager?.flush()
                    webProfile?.webStorage?.deleteAllData()
                }
            }
        }
        val cacheDir = File(getProfileDirectory(profile.storageKey), "cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        historyDao.clearForProfile(profileId)
        savedTabDao.deleteTabsForProfile(profileId)
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private fun generateStorageKey(profileNumber: Int): String {
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(10)
        return "nexo_p_${profileNumber}_$randomSuffix"
    }

    companion object {
        const val MAX_PROFILES = 100
    }
}
