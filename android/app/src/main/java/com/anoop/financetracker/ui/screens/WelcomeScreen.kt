package com.anoop.financetracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.io.ImportResult
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.ConfirmDialog
import com.anoop.financetracker.ui.theme.Ft
import kotlinx.coroutines.launch

/** First run: start with empty default accounts, or bring data over from the website tracker. */
@Composable
fun WelcomeScreen(vm: AppViewModel, st: FinanceState) {
    var currency by remember { mutableStateOf(st.settings.currency) }
    var preview by remember { mutableStateOf<ImportResult?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var askPw by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun read(u: Uri, pw: String?) = scope.launch {
        vm.readBackup(u, pw).onSuccess { preview = it; askPw = false }.onFailure { e ->
            if (e.message?.contains("password", true) == true && pw.isNullOrEmpty()) askPw = true else vm.say(e.message ?: "Couldn't read that file.")
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { uri = u; read(u, null) } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📊", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.size(12.dp))
                Text("Anoop's Finance", style = MaterialTheme.typography.headlineMedium)
                Text("Income, expenses, accounts, budgets and reports. Your data stays encrypted on this phone.",
                    textAlign = TextAlign.Center, color = Ft.c.muted, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
                ChoiceField("Your currency", currency, Money.CURRENCIES.map { Choice(it, it) }, { currency = it }, Modifier.fillMaxWidth(), tag = "welcome-currency")
                Spacer(Modifier.size(16.dp))
                Button(onClick = { vm.startFresh(currency) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-start")) { Text("Start fresh") }
                Text("Creates Bank account, Cash and Credit card with zero balances. You can rename or add more.", style = MaterialTheme.typography.bodySmall,
                    color = Ft.c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                OutlinedButton(onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Import from the Finance Tracker website")
                }
                Text("On the website open Settings → Export backup, then pick that file here.", style = MaterialTheme.typography.bodySmall,
                    color = Ft.c.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    preview?.let { r -> ConfirmDialog("Import this backup?", "Found ${r.summary}.", "Import", { vm.applyImport(r); preview = null }, { preview = null }, destructive = false) }
    if (askPw) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { askPw = false }, title = { Text("Backup password") },
            text = { OutlinedTextField(pw, { pw = it }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)) },
            confirmButton = { TextButton(onClick = { uri?.let { read(it, pw) } }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { askPw = false }) { Text("Cancel") } })
    }
}
