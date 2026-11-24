package com.nexo.browser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NexoProfileAndProxyTest {

    private lateinit var context: Context
    private lateinit var db: NexoDatabase
    private lateinit var securityManager: SecurityManager
    private lateinit var proxyManager: ProxyManager
    private lateinit var profileManager: ProfileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        securityManager = SecurityManager(InMemoryTestKeyProvider())
        proxyManager = ProxyManager(db.proxyDao(), db.profileDao(), securityManager)
        profileManager = ProfileManager(
            context = context,
            profileDao = db.profileDao(),
            bookmarkDao = db.bookmarkDao(),
            historyDao = db.historyDao(),
            downloadDao = db.downloadDao(),
            savedTabDao = db.savedTabDao(),
            proxyManager = proxyManager
        )
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "nexo_profiles").deleteRecursively()
    }

    @Test
    fun `security manager encrypts with unique IV and decrypts accurately`() {
        val secret = "P@ssw0rd!Socks5#99"
        val enc1 = securityManager.encrypt(secret)
        val enc2 = securityManager.encrypt(secret)

        assertTrue(enc1.startsWith("ENCv1:"))
        assertNotEquals(secret, enc1)
        assertNotEquals(enc1, enc2)
        assertEquals(secret, securityManager.decrypt(enc1))
        assertEquals(secret, securityManager.decrypt(enc2))
    }

    @Test
    fun `profile manager creates 100 isolated profiles and enforces max limit`() = runTest {
        val created = profileManager.batchCreateProfilesUpTo(100)
        assertEquals(100, created)

        val profiles = db.profileDao().getAllProfiles()
        assertEquals(100, profiles.size)

        // All storage keys must be unique and directories isolated on disk
        val distinctKeys = profiles.map { it.storageKey }.toSet()
        assertEquals(100, distinctKeys.size)
        profiles.forEach { profile ->
            val dir = profileManager.getProfileDirectory(profile.storageKey)
            assertTrue(dir.exists())
            assertTrue(File(dir, "downloads").exists())
            assertTrue(File(dir, "cache").exists())
        }

        // Attempting to create 101st profile fails cleanly
        val overflow = runCatching {
            profileManager.createProfile("Profile 101")
        }
        assertTrue(overflow.isFailure)
    }

    @Test
    fun `profile bookmarks history and storage remain isolated across profiles`() = runTest {
        val p1 = profileManager.createProfile("Alpha Profile")
        val p2 = profileManager.createProfile("Beta Profile")

        db.bookmarkDao().insert(BookmarkEntity(profileId = p1, title = "Alpha Site", url = "https://alpha.example"))
        db.historyDao().insert(HistoryEntity(profileId = p1, title = "Alpha Visit", url = "https://alpha.example/home"))

        db.bookmarkDao().insert(BookmarkEntity(profileId = p2, title = "Beta Site", url = "https://beta.example"))

        val p1Bookmarks = db.bookmarkDao().observeForProfile(p1).first()
        val p2Bookmarks = db.bookmarkDao().observeForProfile(p2).first()
        val p1History = db.historyDao().observeForProfile(p1).first()
        val p2History = db.historyDao().observeForProfile(p2).first()

        assertEquals(1, p1Bookmarks.size)
        assertEquals("https://alpha.example", p1Bookmarks.first().url)
        assertEquals(1, p2Bookmarks.size)
        assertEquals("https://beta.example", p2Bookmarks.first().url)
        assertEquals(1, p1History.size)
        assertEquals(0, p2History.size)

        // Clearing p1 must not touch p2
        profileManager.clearProfileBrowserStorage(p1)
        assertEquals(0, db.historyDao().observeForProfile(p1).first().size)
        assertEquals(1, db.bookmarkDao().observeForProfile(p2).first().size)
    }

    @Test
    fun `profile clone copies settings and bookmarks with new storage key`() = runTest {
        val proxyId = proxyManager.addProxy("EU Proxy", "198.51.100.4", 1080, "u1", "p1")
        val sourceId = profileManager.createProfile("Main Shop", proxyId, UserAgentPreset.DESKTOP_CHROME)
        db.bookmarkDao().insert(BookmarkEntity(profileId = sourceId, title = "Portal", url = "https://portal.test"))

        val cloneId = profileManager.cloneProfile(sourceId)
        val source = db.profileDao().getProfileById(sourceId)!!
        val clone = db.profileDao().getProfileById(cloneId)!!

        assertNotEquals(source.storageKey, clone.storageKey)
        assertEquals(proxyId, clone.proxyId)
        assertEquals(UserAgentPreset.DESKTOP_CHROME.name, clone.userAgentPreset)
        assertEquals(1, db.bookmarkDao().getForProfile(cloneId).size)
    }

    @Test
    fun `bulk proxy import supports both formats detects duplicates and assigns 100 proxies`() = runTest {
        profileManager.batchCreateProfilesUpTo(100)

        val lines = buildString {
            for (i in 1..50) {
                appendLine("socks5://user$i:pass$i@198.51.100.$i:${1080 + i}")
            }
            for (i in 51..100) {
                appendLine("203.0.113.$i:${2000 + i}:usr$i:pwd$i")
            }
            // Add duplicates & invalid lines
            appendLine("socks5://user1:pass1@198.51.100.1:1081")
            appendLine("http://invalid-scheme:8080")
            appendLine("bad_line_no_port")
        }

        val parsed = proxyManager.parseBulkProxies(lines)
        assertEquals(100, parsed.validEntries.size)
        assertEquals(1, parsed.duplicateLines.size)
        assertEquals(2, parsed.invalidLines.size)

        val outcome = proxyManager.importParsedProxies(
            entries = parsed.validEntries,
            autoAssignToProfiles = true,
            autoTestAfterImport = false
        )
        assertEquals(100, outcome.importedCount)
        assertEquals(100, outcome.assignedCount)

        // Passwords in DB must be encrypted
        val storedProxies = db.proxyDao().getAllProxies()
        assertEquals(100, storedProxies.size)
        assertTrue(storedProxies.all { it.encryptedPassword.startsWith("ENCv1:") })
        assertFalse(storedProxies.any { it.encryptedPassword == "pass1" })

        val assignedProfiles = db.profileDao().getAllProfiles().count { it.proxyId != null }
        assertEquals(100, assignedProfiles)
    }

    @Test
    fun `browser address formatter resolves urls and search queries`() {
        assertEquals(
            "https://example.com/path",
            BrowserEngine.formatAddressOrSearch("example.com/path", SearchEngine.GOOGLE)
        )
        assertEquals(
            "http://127.0.0.1:8080",
            BrowserEngine.formatAddressOrSearch("http://127.0.0.1:8080", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://duckduckgo.com/?q=nexo+browser",
            BrowserEngine.formatAddressOrSearch("nexo browser", SearchEngine.DUCKDUCKGO)
        )
    }
}
