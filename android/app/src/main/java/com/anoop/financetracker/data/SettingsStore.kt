package com.anoop.financetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val currency: String = "KWD",
    val theme: String = "dark",
    val lockEnabled: Boolean = false,
    val biometric: Boolean = false,
    val pinHash: String = "",
    val autoLockMinutes: Int = 1,
    val notifyBills: Boolean = false,
    val notifyRecurring: Boolean = false,
    val notifyBudget: Boolean = false,
    val notifyGoals: Boolean = false,
    val notifySummary: Boolean = false,
    val lastBackupAt: Long = 0L,
    val welcomeDone: Boolean = false,
) {
    val anyNotifications get() = notifyBills || notifyRecurring || notifyBudget || notifyGoals || notifySummary
}

/** Small settings interface so ViewModels can be tested with fakes or mocks. */
interface SettingsSource {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(block: (AppSettings) -> AppSettings)
    suspend fun firedKeys(): Set<String>
    suspend fun addFired(keys: Set<String>)
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) : SettingsSource {
    private object K {
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val lock = booleanPreferencesKey("lock")
        val bio = booleanPreferencesKey("biometric")
        val pin = stringPreferencesKey("pin_hash")
        val autoLock = intPreferencesKey("auto_lock_min")
        val nBills = booleanPreferencesKey("n_bills")
        val nRec = booleanPreferencesKey("n_recurring")
        val nBud = booleanPreferencesKey("n_budget")
        val nGoal = booleanPreferencesKey("n_goals")
        val nSum = booleanPreferencesKey("n_summary")
        val lastBackup = longPreferencesKey("last_backup")
        val welcome = booleanPreferencesKey("welcome_done")
        val fired = stringSetPreferencesKey("fired")
    }

    override val settings: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            currency = p[K.currency] ?: "KWD",
            theme = p[K.theme] ?: "dark",
            lockEnabled = p[K.lock] ?: false,
            biometric = p[K.bio] ?: false,
            pinHash = p[K.pin] ?: "",
            autoLockMinutes = p[K.autoLock] ?: 1,
            notifyBills = p[K.nBills] ?: false,
            notifyRecurring = p[K.nRec] ?: false,
            notifyBudget = p[K.nBud] ?: false,
            notifyGoals = p[K.nGoal] ?: false,
            notifySummary = p[K.nSum] ?: false,
            lastBackupAt = p[K.lastBackup] ?: 0L,
            welcomeDone = p[K.welcome] ?: false,
        )
    }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(block: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val cur = AppSettings(
                currency = p[K.currency] ?: "KWD", theme = p[K.theme] ?: "dark",
                lockEnabled = p[K.lock] ?: false, biometric = p[K.bio] ?: false, pinHash = p[K.pin] ?: "",
                autoLockMinutes = p[K.autoLock] ?: 1, notifyBills = p[K.nBills] ?: false,
                notifyRecurring = p[K.nRec] ?: false, notifyBudget = p[K.nBud] ?: false,
                notifyGoals = p[K.nGoal] ?: false, notifySummary = p[K.nSum] ?: false,
                lastBackupAt = p[K.lastBackup] ?: 0L, welcomeDone = p[K.welcome] ?: false,
            )
            val n = block(cur)
            p[K.currency] = n.currency; p[K.theme] = n.theme; p[K.lock] = n.lockEnabled; p[K.bio] = n.biometric
            p[K.pin] = n.pinHash; p[K.autoLock] = n.autoLockMinutes; p[K.nBills] = n.notifyBills
            p[K.nRec] = n.notifyRecurring; p[K.nBud] = n.notifyBudget; p[K.nGoal] = n.notifyGoals
            p[K.nSum] = n.notifySummary; p[K.lastBackup] = n.lastBackupAt; p[K.welcome] = n.welcomeDone
        }
    }

    override suspend fun firedKeys(): Set<String> = context.store.data.first()[K.fired] ?: emptySet()

    override suspend fun addFired(keys: Set<String>) {
        context.store.edit { p ->
            // Keep the most recent 300 keys so the set does not grow forever.
            val all = (p[K.fired] ?: emptySet()) + keys
            p[K.fired] = if (all.size > 300) all.toList().takeLast(300).toSet() else all
        }
    }
}
