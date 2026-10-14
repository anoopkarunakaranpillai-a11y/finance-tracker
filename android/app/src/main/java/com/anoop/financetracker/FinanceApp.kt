package com.anoop.financetracker

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.anoop.financetracker.data.AppDatabase
import com.anoop.financetracker.data.FinanceRepository
import com.anoop.financetracker.data.SettingsSource
import com.anoop.financetracker.data.SettingsStore
import com.anoop.financetracker.security.DatabaseKey
import com.anoop.financetracker.work.Notifier

/** Simple manual dependency container (no DI framework needed for an app this size). */
class AppContainer(val context: Context, val db: AppDatabase, val settings: SettingsSource) {
    val repo = FinanceRepository(db)
}

open class FinanceApp : Application(), Configuration.Provider {
    @Volatile private var container: AppContainer? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }

    /** Builds the container on first use. Tests can replace it with [override]. */
    fun container(): AppContainer? {
        container?.let { return it }
        synchronized(this) {
            container?.let { return it }
            val c = override ?: createContainer()
            container = c
            return c
        }
    }

    protected open fun createContainer(): AppContainer {
        val pass = DatabaseKey(this).passphrase()
        return AppContainer(this, AppDatabase.encrypted(this, pass), SettingsStore(this))
    }

    companion object {
        /** Set by instrumented/Robolectric tests before the activity starts. */
        @Volatile var override: AppContainer? = null
    }
}
