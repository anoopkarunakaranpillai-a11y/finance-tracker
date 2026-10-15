package com.anoop.financetracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.security.PinHasher
import com.anoop.financetracker.ui.AppRoot
import com.anoop.financetracker.ui.screens.LockScreen
import com.anoop.financetracker.ui.theme.FinanceTheme
import com.anoop.financetracker.work.DailyWorker

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(true)
    private var lockChecked by mutableStateOf(false)
    private var latest: AppSettings? = null
    private var backgroundAt = 0L

    private val lifecycleWatcher = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
        override fun onStart(owner: LifecycleOwner) {
            val s = latest ?: return
            if (s.lockEnabled && backgroundAt > 0 && System.currentTimeMillis() - backgroundAt >= s.autoLockMinutes * 60_000L) locked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FinanceApp).container() ?: run { finish(); return }
        DailyWorker.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleWatcher)

        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s = settings
            LaunchedEffect(s) {
                if (s != null) {
                    latest = s
                    if (!lockChecked) { locked = s.lockEnabled; lockChecked = true }
                    if (!s.lockEnabled) locked = false
                    // Hide content from screenshots and the recent-apps preview while App lock is on.
                    if (s.lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            FinanceTheme(s?.theme ?: "dark") {
                Root(s, container)
            }
        }
    }

    @Composable
    private fun Root(s: AppSettings?, container: AppContainer) {
        when {
            s == null || !lockChecked -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            s.lockEnabled && locked -> LockScreen(
                biometric = s.biometric && canUseBiometric(),
                onBiometric = { promptBiometric() },
                check = { pin -> val ok = PinHasher.verify(pin, s.pinHash); if (ok) locked = false; ok },
            )
            else -> AppRoot(container, deepLink = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it in DEEP_LINKS })
        }
    }

    private fun canUseBiometric() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric() {
        if (!canUseBiometric()) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Anoop's Finance")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Use PIN")
                .build()
        )
    }

    companion object {
        /** Optional screen to open at launch, e.g. "study/navw" (used by tests and future home-screen shortcuts). */
        const val EXTRA_ROUTE = "route"
        val DEEP_LINKS = setOf("study", "study/navw", "study/arya", "transactions", "plan", "reports", "settings", "accounts")
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleWatcher)
        super.onDestroy()
    }
}
