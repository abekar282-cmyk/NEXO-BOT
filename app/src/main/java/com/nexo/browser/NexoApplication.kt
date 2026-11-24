package com.nexo.browser

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NexoApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: NexoDatabase
        private set
    lateinit var securityManager: SecurityManager
        private set
    lateinit var proxyManager: ProxyManager
        private set
    lateinit var profileManager: ProfileManager
        private set
    lateinit var settingsStore: NexoSettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        database = NexoDatabase.getInstance(this)
        securityManager = SecurityManager()
        proxyManager = ProxyManager(
            proxyDao = database.proxyDao(),
            profileDao = database.profileDao(),
            securityManager = securityManager
        )
        profileManager = ProfileManager(
            context = this,
            profileDao = database.profileDao(),
            bookmarkDao = database.bookmarkDao(),
            historyDao = database.historyDao(),
            downloadDao = database.downloadDao(),
            savedTabDao = database.savedTabDao(),
            proxyManager = proxyManager
        )
        settingsStore = NexoSettingsStore(this)

        appScope.launch {
            profileManager.seedInitialProfilesIfEmpty(initialCount = 6)
        }
    }
}
