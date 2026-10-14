package com.anoop.financetracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anoop.financetracker.data.AppDatabase
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.data.SettingsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory settings for tests (DataStore keeps a process-wide file handle that does not suit repeated tests). */
class FakeSettings(initial: AppSettings = AppSettings()) : SettingsSource {
    val flow = MutableStateFlow(initial)
    private val fired = HashSet<String>()
    override val settings: Flow<AppSettings> = flow
    override suspend fun current() = flow.value
    override suspend fun update(block: (AppSettings) -> AppSettings) { flow.value = block(flow.value) }
    override suspend fun firedKeys(): Set<String> = fired.toSet()
    override suspend fun addFired(keys: Set<String>) { fired += keys }
}

object TestEnv {
    fun context(): Context = ApplicationProvider.getApplicationContext()
    fun memoryDb(): AppDatabase = AppDatabase.plain(context(), null)
    fun container(settings: AppSettings = AppSettings(welcomeDone = true)): AppContainer =
        AppContainer(context(), memoryDb(), FakeSettings(settings))
}
