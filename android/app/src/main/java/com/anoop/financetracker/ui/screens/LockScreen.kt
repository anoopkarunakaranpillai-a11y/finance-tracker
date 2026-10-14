package com.anoop.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anoop.financetracker.ui.theme.Ft
import kotlinx.coroutines.delay

/**
 * PIN pad. After 5 wrong attempts the pad pauses for 30 seconds (doubling each time) to slow down guessing.
 */
@Composable
fun LockScreen(biometric: Boolean, onBiometric: () -> Unit, check: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var msg by remember { mutableStateOf("Enter your PIN") }
    LaunchedEffect(Unit) { if (biometric) onBiometric() }
    LaunchedEffect(waitUntil) { while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    val blocked = now < waitUntil

    fun press(d: String) {
        if (blocked) return
        pin = (pin + d).take(8)
    }
    fun submit() {
        if (blocked || pin.length < 4) return
        if (check(pin)) { pin = ""; wrong = 0; return }
        wrong++; pin = ""
        if (wrong % 5 == 0) {
            val secs = 30L shl ((wrong / 5) - 1).coerceAtMost(5)
            waitUntil = System.currentTimeMillis() + secs * 1000
            msg = "Too many tries. Wait a moment."
        } else msg = "Wrong PIN. Try again."
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🔒", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.size(8.dp))
            Text("Anoop's Finance is locked", style = MaterialTheme.typography.titleLarge)
            Text(if (blocked) "Try again in ${(waitUntil - now) / 1000 + 1}s" else msg, color = Ft.c.muted, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { contentDescription = "${pin.length} digits entered" }) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).padding(1.dp)) {
                        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = if (i < pin.length) MaterialTheme.colorScheme.primary else Ft.c.line) {}
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { k ->
                        FilledTonalButton(
                            onClick = { when (k) { "⌫" -> pin = pin.dropLast(1); "OK" -> submit(); else -> press(k) } },
                            enabled = !blocked, shape = CircleShape, modifier = Modifier.size(72.dp).testTag("pin-$k"),
                        ) { Text(k, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            if (biometric) TextButton(onClick = onBiometric, modifier = Modifier.padding(top = 12.dp)) { Text("Use fingerprint or face") }
        }
    }
}
