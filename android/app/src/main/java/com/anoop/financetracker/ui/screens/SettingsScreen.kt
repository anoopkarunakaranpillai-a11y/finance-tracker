package com.anoop.financetracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.anoop.financetracker.BuildConfig
import com.anoop.financetracker.data.AppSettings
import com.anoop.financetracker.data.CategoryEntity
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.domain.Validation
import com.anoop.financetracker.io.ImportResult
import com.anoop.financetracker.security.PinHasher
import com.anoop.financetracker.ui.AppViewModel
import com.anoop.financetracker.ui.FinanceState
import com.anoop.financetracker.ui.components.Choice
import com.anoop.financetracker.ui.components.ChoiceField
import com.anoop.financetracker.ui.components.ConfirmDialog
import com.anoop.financetracker.ui.components.Page
import com.anoop.financetracker.ui.components.SectionCard
import com.anoop.financetracker.ui.components.SubPage
import com.anoop.financetracker.ui.theme.Ft
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(st: FinanceState, vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = st.settings
    var pinDialog by remember { mutableStateOf(false) }
    var exportPw by remember { mutableStateOf<String?>(null) }
    var askExport by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importPwNeeded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportResult?>(null) }
    var pendingNotif by remember { mutableStateOf<((AppSettings) -> AppSettings)?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val f = pendingNotif
        pendingNotif = null
        if (granted && f != null) vm.updateSettings(f) else if (!granted) vm.say("Notifications are blocked. You can allow them in Android Settings → Apps.")
    }
    fun setNotif(f: (AppSettings) -> AppSettings, turningOn: Boolean) {
        val needs = turningOn && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) { pendingNotif = f; notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) } else vm.updateSettings(f)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { vm.say(if (vm.exportBackup(uri, exportPw)) "Backup saved. Keep it somewhere safe." else "Couldn't save the backup.") }
    }
    fun readImport(uri: Uri, pw: String?) = scope.launch {
        vm.readBackup(uri, pw).onSuccess { preview = it; importPwNeeded = false }.onFailure { e ->
            if (e.message?.contains("password", true) == true && pw.isNullOrEmpty()) importPwNeeded = true else vm.say(e.message ?: "Couldn't read that backup.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importUri = uri; readImport(uri, null) }
    }
    val bioOk = remember { BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }

    Page("Settings") {
        item {
            SectionCard("General") {
                ChoiceField("Currency", s.currency, Money.CURRENCIES.map { Choice(it, it) }, { c -> vm.updateSettings { it.copy(currency = c) } }, tag = "currency")
                Text("Amounts are shown in this currency. Changing it does not convert existing amounts.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                Spacer(Modifier.size(8.dp))
                ChoiceField("Theme", s.theme, listOf(Choice("dark", "Dark"), Choice("light", "Light"), Choice("system", "Follow system")), { t -> vm.updateSettings { it.copy(theme = t) } }, tag = "theme")
                Spacer(Modifier.size(4.dp))
                LinkRow("🏦  Accounts") { nav.navigate("accounts") }
                LinkRow("🏷️  Categories") { nav.navigate("categories") }
                LinkRow("🎓  Study corner (Navamika & Arya)") { nav.navigate("study") }
            }
        }
        item {
            SectionCard("Security") {
                ToggleRow("App lock (PIN)", "Ask for a PIN when the app opens or returns after a while.", s.lockEnabled, "lock") { on ->
                    if (on) pinDialog = true else vm.disableLock()
                }
                if (s.lockEnabled) {
                    if (bioOk) ToggleRow("Unlock with fingerprint or face", null, s.biometric, "bio") { on -> vm.updateSettings { it.copy(biometric = on) } }
                    ChoiceField("Lock again after", s.autoLockMinutes.toString(), listOf(0, 1, 5, 15).map { Choice(it.toString(), if (it == 0) "Immediately" else "$it minute${if (it == 1) "" else "s"} in the background") },
                        { v -> vm.updateSettings { it.copy(autoLockMinutes = v.toInt()) } })
                    TextButton(onClick = { pinDialog = true }) { Text("Change PIN") }
                }
                Text("Your data is stored only on this phone in an encrypted database. Screenshots and the recent-apps preview are blocked while App lock is on.",
                    style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            }
        }
        item {
            SectionCard("Notifications") {
                ToggleRow("Upcoming bills", "A few days before each due date", s.notifyBills, "n-bills") { on -> setNotif({ it.copy(notifyBills = on) }, on) }
                ToggleRow("Recurring transactions", "When the app records one for you", s.notifyRecurring, "n-rec") { on -> setNotif({ it.copy(notifyRecurring = on) }, on) }
                ToggleRow("Budget limits", "At 80% and 100% of a category budget", s.notifyBudget, "n-bud") { on -> setNotif({ it.copy(notifyBudget = on) }, on) }
                ToggleRow("Savings milestones", "At 25%, 50%, 75% and 100% of a goal", s.notifyGoals, "n-goal") { on -> setNotif({ it.copy(notifyGoals = on) }, on) }
                ToggleRow("Monthly summary", "On the 1st of each month", s.notifySummary, "n-sum") { on -> setNotif({ it.copy(notifySummary = on) }, on) }
                Text("Checked once a day around 9:00 AM. Amounts are hidden on the lock screen.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            }
        }
        item {
            SectionCard("Backup & restore") {
                Text(if (s.lastBackupAt > 0) "Last backup: " + DateFormat.getDateTimeInstance().format(Date(s.lastBackupAt)) else "No backup yet. Android's cloud backup is switched off for this app to keep your data private, so save your own.",
                    style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { askExport = true }, modifier = Modifier.testTag("backup-export")) { Text("Save backup") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.testTag("backup-import")) { Text("Restore / import") }
                }
                Text("Restore accepts this app's backups and the JSON backup from the Finance Tracker website (Settings → Export backup).",
                    style = MaterialTheme.typography.bodySmall, color = Ft.c.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            SectionCard("About") {
                Text("Anoop's Finance ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text("Finance data never leaves this phone unless you export it. The Study corner loads Navamika's and Arya's pages from the public website.",
                    style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            }
        }
    }

    if (pinDialog) PinSetupDialog(bioOk, onDone = { pin, bio -> vm.setPin(pin, bio); pinDialog = false }, onDismiss = { pinDialog = false })

    if (askExport) {
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { askExport = false }, title = { Text("Save backup") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a password to encrypt the file (recommended). Without the password the backup can't be restored, so don't lose it.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pw, { pw = it }, label = { Text("Password (optional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (pw.isNotEmpty()) OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = pw2.isNotEmpty() && pw2 != pw, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (pw.isEmpty()) Text("Without a password the file is plain JSON that the Finance Tracker website can also import.", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
            } },
            confirmButton = { TextButton(enabled = pw.isEmpty() || (pw.length >= 6 && pw == pw2), onClick = {
                exportPw = pw.ifEmpty { null }; askExport = false
                exportLauncher.launch("finance-backup-${java.time.LocalDate.now()}${if (pw.isNotEmpty()) "-encrypted" else ""}.json")
            }) { Text("Choose where to save") } },
            dismissButton = { TextButton(onClick = { askExport = false }) { Text("Cancel") } })
    }

    if (importPwNeeded) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { importPwNeeded = false }, title = { Text("Password-protected backup") },
            text = { OutlinedTextField(pw, { pw = it }, label = { Text("Backup password") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = { TextButton(onClick = { importUri?.let { readImport(it, pw) } }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { importPwNeeded = false }) { Text("Cancel") } })
    }

    preview?.let { r ->
        ConfirmDialog("Replace data with this backup?",
            "Found ${r.summary}.\n\nEverything currently in the app will be replaced. Save a backup first if you may need it.",
            "Replace", onConfirm = { vm.applyImport(r); preview = null }, onDismiss = { preview = null })
    }
}

@Composable
private fun ToggleRow(title: String, sub: String?, value: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!value) }.padding(vertical = 6.dp).testTag("toggle-$tag"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
        }
        Switch(value, null)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp))
}

@Composable
fun PinSetupDialog(bioAvailable: Boolean, onDone: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(bioAvailable) }
    val err = when {
        pin.isNotEmpty() && !PinHasher.isValidPin(pin) -> "Use 4 to 8 digits."
        pin2.isNotEmpty() && pin2 != pin -> "The PINs don't match."
        else -> null
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Set a PIN") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("New PIN (4–8 digits)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.testTag("pin-1"))
            OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, label = { Text("Repeat PIN") }, singleLine = true, isError = err != null,
                supportingText = err?.let { { Text(it) } },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.testTag("pin-2"))
            if (bioAvailable) Row(verticalAlignment = Alignment.CenterVertically) { Text("Also allow fingerprint / face", Modifier.weight(1f)); Switch(bio, { bio = it }) }
        } },
        confirmButton = { TextButton(enabled = err == null && PinHasher.isValidPin(pin) && pin == pin2, onClick = { onDone(pin, bio) }, modifier = Modifier.testTag("pin-save")) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun CategoriesScreen(st: FinanceState, vm: AppViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏷️") }
    var kind by remember { mutableStateOf(TxType.EXPENSE) }
    var err by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<String?>(null) }
    SubPage("Categories", onBack) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard("Add a category") {
                    ChoiceField("For", kind, listOf(Choice(TxType.EXPENSE, "Expenses"), Choice(TxType.INCOME, "Income")), { kind = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.weight(0.3f))
                        OutlinedTextField(name, { name = it.take(40); err = null }, label = { Text("Name") }, singleLine = true, isError = err != null,
                            supportingText = err?.let { { Text(it) } }, modifier = Modifier.weight(0.7f).testTag("field-cat-name"))
                    }
                    Button(onClick = {
                        val e = Validation.validateName(name, "category")
                        val key = "c_" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").take(30)
                        when {
                            e != null -> err = e
                            (st.custom.any { it.key == key }) -> err = "You already have this category."
                            else -> { vm.saveCategory(CategoryEntity(key, name.trim(), emoji.ifBlank { "🏷️" }, kind)); name = "" }
                        }
                    }, modifier = Modifier.testTag("cat-save")) { Text("Add") }
                }
            }
            item { Text("Your categories", style = MaterialTheme.typography.titleMedium) }
            if (st.custom.isEmpty()) item { Text("None yet. The built-in categories from the tracker are always available.", color = Ft.c.muted) }
            items(st.custom, key = { it.key }) { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.emoji}  ${c.name}", modifier = Modifier.weight(1f))
                    Text(if (c.kind == TxType.INCOME) "Income" else "Expense", color = Ft.c.muted)
                    TextButton(onClick = { del = c.key }) { Text("Remove") }
                }
                HorizontalDivider(color = Ft.c.line)
            }
        }
    }
    del?.let { k -> ConfirmDialog("Remove this category?", "Existing transactions keep it; it just won't be offered for new ones.", "Remove",
        { vm.deleteCategory(k); del = null }, { del = null }) }
}
