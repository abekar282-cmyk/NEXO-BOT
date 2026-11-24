package com.nexo.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ProfileStatus {
    IDLE,
    ACTIVE,
    PROXY_ERROR
}

enum class ProxyStatus {
    UNTESTED,
    TESTING,
    WORKING,
    FAILED,
    CONNECTED
}

enum class SearchEngine(val label: String, val searchUrlTemplate: String, val homeUrl: String) {
    GOOGLE(
        label = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        homeUrl = "https://www.google.com"
    ),
    DUCKDUCKGO(
        label = "DuckDuckGo",
        searchUrlTemplate = "https://duckduckgo.com/?q=%s",
        homeUrl = "https://duckduckgo.com"
    ),
    BRAVE(
        label = "Brave Search",
        searchUrlTemplate = "https://search.brave.com/search?q=%s",
        homeUrl = "https://search.brave.com"
    ),
    STARTPAGE(
        label = "Startpage",
        searchUrlTemplate = "https://www.startpage.com/do/search?query=%s",
        homeUrl = "https://www.startpage.com"
    ),
    BING(
        label = "Bing",
        searchUrlTemplate = "https://www.bing.com/search?q=%s",
        homeUrl = "https://www.bing.com"
    )
}

enum class UserAgentPreset(val label: String, val userAgent: String) {
    MOBILE_CHROME(
        label = "Android Chrome",
        userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    ),
    DESKTOP_CHROME(
        label = "Desktop Chrome",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    PIXEL_MOBILE(
        label = "Pixel Compact",
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    )
}

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "SOCKS5",
    val host: String,
    val port: Int,
    val username: String = "",
    val encryptedPassword: String = "",
    val status: ProxyStatus = ProxyStatus.UNTESTED,
    val publicIp: String? = null,
    val countryCode: String? = null,
    val latencyMs: Long? = null,
    val lastError: String? = null,
    val lastCheckedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = ProxyEntity::class,
            parentColumns = ["id"],
            childColumns = ["proxyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("proxyId"), Index(value = ["profileNumber"], unique = true)]
)
data class BrowserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileNumber: Int,
    val name: String,
    val storageKey: String,
    val proxyId: Long? = null,
    val status: ProfileStatus = ProfileStatus.IDLE,
    val lastPublicIp: String? = null,
    val lastLatencyMs: Long? = null,
    val userAgentPreset: String = UserAgentPreset.MOBILE_CHROME.name,
    val isIncognitoDefault: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null
)

data class ProfileWithProxy(
    @Embedded val profile: BrowserProfileEntity,
    @Relation(
        parentColumn = "proxyId",
        entityColumn = "id"
    )
    val proxy: ProxyEntity?
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BrowserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = BrowserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = BrowserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val fileName: String,
    val url: String,
    val mimeType: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val status: String = "DOWNLOADING",
    val localPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_tabs",
    foreignKeys = [
        ForeignKey(
            entity = BrowserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class SavedTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val tabOrder: Int,
    val title: String,
    val url: String,
    val isActive: Boolean = false,
    val isIncognito: Boolean = false
)

class NexoTypeConverters {
    @TypeConverter
    fun fromProfileStatus(value: ProfileStatus): String = value.name

    @TypeConverter
    fun toProfileStatus(value: String): ProfileStatus =
        runCatching { ProfileStatus.valueOf(value) }.getOrDefault(ProfileStatus.IDLE)

    @TypeConverter
    fun fromProxyStatus(value: ProxyStatus): String = value.name

    @TypeConverter
    fun toProxyStatus(value: String): ProxyStatus =
        runCatching { ProxyStatus.valueOf(value) }.getOrDefault(ProxyStatus.UNTESTED)
}

@Dao
interface ProfileDao {
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY profileNumber ASC")
    fun observeProfilesWithProxy(): Flow<List<ProfileWithProxy>>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    fun observeProfileWithProxy(profileId: Long): Flow<ProfileWithProxy?>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileWithProxy(profileId: Long): ProfileWithProxy?

    @Query("SELECT * FROM profiles ORDER BY profileNumber ASC")
    suspend fun getAllProfiles(): List<BrowserProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: Long): BrowserProfileEntity?

    @Query("SELECT MAX(profileNumber) FROM profiles")
    suspend fun getMaxProfileNumber(): Int?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: BrowserProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfiles(profiles: List<BrowserProfileEntity>): List<Long>

    @Update
    suspend fun updateProfile(profile: BrowserProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: Long)

    @Query("UPDATE profiles SET status = :status, lastOpenedAt = :lastOpenedAt WHERE id = :profileId")
    suspend fun updateStatus(profileId: Long, status: ProfileStatus, lastOpenedAt: Long?)

    @Query("UPDATE profiles SET lastPublicIp = :publicIp, lastLatencyMs = :latencyMs WHERE id = :profileId")
    suspend fun updatePublicIp(profileId: Long, publicIp: String?, latencyMs: Long?)

    @Query("UPDATE profiles SET lastPublicIp = :publicIp, lastLatencyMs = :latencyMs WHERE proxyId = :proxyId")
    suspend fun updatePublicIpForProxy(proxyId: Long, publicIp: String?, latencyMs: Long?)

    @Query("UPDATE profiles SET proxyId = :proxyId WHERE id = :profileId")
    suspend fun assignProxy(profileId: Long, proxyId: Long?)
}

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY id DESC")
    fun observeAllProxies(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies ORDER BY id ASC")
    suspend fun getAllProxies(): List<ProxyEntity>

    @Query("SELECT * FROM proxies WHERE id = :proxyId")
    suspend fun getProxyById(proxyId: Long): ProxyEntity?

    @Query("SELECT * FROM proxies WHERE host = :host AND port = :port AND username = :username LIMIT 1")
    suspend fun findDuplicate(host: String, port: Int, username: String): ProxyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxies(proxies: List<ProxyEntity>): List<Long>

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Query("DELETE FROM proxies WHERE id = :proxyId")
    suspend fun deleteProxyById(proxyId: Long)

    @Query(
        "UPDATE proxies SET status = :status, publicIp = :publicIp, countryCode = :countryCode, " +
            "latencyMs = :latencyMs, lastError = :lastError, lastCheckedAt = :checkedAt WHERE id = :proxyId"
    )
    suspend fun updateCheckResult(
        proxyId: Long,
        status: ProxyStatus,
        publicIp: String?,
        countryCode: String?,
        latencyMs: Long?,
        lastError: String?,
        checkedAt: Long
    )
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun getForProfile(profileId: Long): List<BookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE profileId = :profileId AND url = :url)")
    fun observeIsBookmarked(profileId: Long, url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId AND url = :url")
    suspend fun deleteByUrl(profileId: Long, url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC LIMIT 300")
    fun observeForProfile(profileId: Long): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)
}

@Dao
interface SavedTabDao {
    @Query("SELECT * FROM saved_tabs WHERE profileId = :profileId ORDER BY tabOrder ASC")
    suspend fun getTabsForProfile(profileId: Long): List<SavedTabEntity>

    @Query("DELETE FROM saved_tabs WHERE profileId = :profileId")
    suspend fun deleteTabsForProfile(profileId: Long)

    @Insert
    suspend fun insertTabs(tabs: List<SavedTabEntity>)

    @Transaction
    suspend fun replaceTabsForProfile(profileId: Long, tabs: List<SavedTabEntity>) {
        deleteTabsForProfile(profileId)
        if (tabs.isNotEmpty()) {
            insertTabs(tabs)
        }
    }
}

@Database(
    entities = [
        BrowserProfileEntity::class,
        ProxyEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        SavedTabEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(NexoTypeConverters::class)
abstract class NexoDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun proxyDao(): ProxyDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun savedTabDao(): SavedTabDao

    companion object {
        @Volatile
        private var instance: NexoDatabase? = null

        fun getInstance(context: Context): NexoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoDatabase::class.java,
                    "nexo_browser.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

private val Context.nexoDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexo_settings")

data class AppSettings(
    val defaultSearchEngine: SearchEngine = SearchEngine.GOOGLE,
    val strictProxyLock: Boolean = true,
    val forceDarkWebContent: Boolean = true,
    val webrtcProtection: Boolean = true,
    val proxyTimeoutMs: Int = 12_000
)

class NexoSettingsStore(private val context: Context) {
    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("default_search_engine")
        val STRICT_PROXY_LOCK = booleanPreferencesKey("strict_proxy_lock")
        val FORCE_DARK_WEB = booleanPreferencesKey("force_dark_web")
        val WEBRTC_PROTECTION = booleanPreferencesKey("webrtc_protection")
        val PROXY_TIMEOUT_MS = intPreferencesKey("proxy_timeout_ms")
    }

    val settingsFlow: Flow<AppSettings> = context.nexoDataStore.data.map { prefs ->
        val engine = runCatching {
            SearchEngine.valueOf(prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.name)
        }.getOrDefault(SearchEngine.GOOGLE)

        AppSettings(
            defaultSearchEngine = engine,
            strictProxyLock = prefs[Keys.STRICT_PROXY_LOCK] ?: true,
            forceDarkWebContent = prefs[Keys.FORCE_DARK_WEB] ?: true,
            webrtcProtection = prefs[Keys.WEBRTC_PROTECTION] ?: true,
            proxyTimeoutMs = prefs[Keys.PROXY_TIMEOUT_MS] ?: 12_000
        )
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.nexoDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setStrictProxyLock(enabled: Boolean) {
        context.nexoDataStore.edit { it[Keys.STRICT_PROXY_LOCK] = enabled }
    }

    suspend fun setForceDarkWebContent(enabled: Boolean) {
        context.nexoDataStore.edit { it[Keys.FORCE_DARK_WEB] = enabled }
    }

    suspend fun setWebRtcProtection(enabled: Boolean) {
        context.nexoDataStore.edit { it[Keys.WEBRTC_PROTECTION] = enabled }
    }

    suspend fun setProxyTimeoutMs(timeoutMs: Int) {
        context.nexoDataStore.edit { it[Keys.PROXY_TIMEOUT_MS] = timeoutMs.coerceIn(4_000, 30_000) }
    }
}
