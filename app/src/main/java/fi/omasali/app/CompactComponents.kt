package fi.omasali.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class MenuAction(val label: String, val enabled: Boolean = true, val action: () -> Unit)
@Composable fun MoreMenu(description: String, actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = description }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
        DropdownMenu(expanded, { expanded = false }) { actions.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, enabled = item.enabled, onClick = { expanded = false; item.action() }) } }
    }
}
@Composable fun CompactRow(title: String, subtitle: String, leading: String = "", onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (leading.isNotBlank()) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(leading, color = MaterialTheme.colorScheme.primary) } }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}
@Composable fun ConfirmDialog(title: String, message: String, close: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { confirm(); close() }) { Text("Vahvista") } }, dismissButton = { TextButton(onClick = close) { Text("Peruuta") } })
}
@Composable fun ChipStrip(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = choices.isNotEmpty()) {
                Text(selected, Modifier.weight(1f), maxLines = 2); Text("⌄")
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { choose(id); expanded = false }) }
            }
        }
    }
}
