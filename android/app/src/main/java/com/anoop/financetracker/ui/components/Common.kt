package com.anoop.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anoop.financetracker.data.TxEntity
import com.anoop.financetracker.domain.Categories
import com.anoop.financetracker.domain.Category
import com.anoop.financetracker.domain.Money
import com.anoop.financetracker.domain.TxType
import com.anoop.financetracker.ui.theme.Ft

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Ft.c.card,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(title ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    action?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Ft.c.muted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.size(14.dp)); action() }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmojiBadge(emoji: String, tint: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun signedColor(type: String): Color = when (type) {
    TxType.INCOME -> Ft.c.income
    TxType.EXPENSE -> Ft.c.expense
    else -> Ft.c.transfer
}

/** One transaction row. 48dp+ touch target, readable at large font sizes. */
@Composable
fun TxRow(t: TxEntity, accountName: (String) -> String, currency: String, custom: List<Category>, onClick: () -> Unit) {
    val cat = Categories.find(t.category, custom)
    val col = signedColor(t.type)
    val title = when {
        t.type == TxType.TRANSFER -> "${accountName(t.accountId)} → ${t.toAccountId?.let(accountName) ?: "?"}"
        t.merchant.isNotBlank() -> t.merchant
        t.note.isNotBlank() -> t.note
        else -> cat.name
    }
    val sub = buildString {
        append(if (t.type == TxType.TRANSFER) "Transfer" else cat.name)
        append(" · ").append(t.date)
        if (t.type != TxType.TRANSFER) append(" · ").append(accountName(t.accountId))
    }
    val amount = when (t.type) {
        TxType.INCOME -> Money.format(t.amountMils, currency, signed = true)
        TxType.EXPENSE -> Money.format(-t.amountMils, currency)
        else -> Money.format(t.amountMils, currency)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Open transaction", onClick = onClick)
            .padding(vertical = 8.dp).testTag("tx-${t.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(cat.emoji, col)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Ft.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = col)
    }
}

data class Choice(val key: String, val label: String)

/** Read-only dropdown field for picking one option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceField(label: String, value: String, choices: List<Choice>, onPick: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, tag: String = label) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = choices.firstOrNull { it.key == value }?.label ?: "",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable).testTag("field-$tag"),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = { onPick(c.key); open = false }, modifier = Modifier.testTag("opt-${c.key}"))
            }
        }
    }
}

@Composable
fun KeyValue(k: String, v: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = Ft.c.muted, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(v, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.End, modifier = Modifier.weight(1.3f, fill = false))
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier, sub: String? = null) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Ft.c.card) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Ft.c.muted)
            Spacer(Modifier.size(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Ft.c.muted, maxLines = 2)
        }
    }
}
