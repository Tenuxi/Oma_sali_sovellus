package fi.omasali.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.ByteArrayOutputStream

fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Tiedosto ylittää kokorajan" }; out.write(buffer, 0, n) }
    return out.toByteArray()
}
fun CoroutineScope.launchSafe(error: (String) -> Unit, block: suspend () -> Unit) = launch {
    try { block() } catch (e: Exception) { if (e is CancellationException) throw e; error(e.message ?: "Toiminto epäonnistui") }
}
data class ImportPreview(val baseline: List<Record>, val backup: Backup, val items: List<MergeItem>)
fun recordLabel(r: Record): String = when (r.kind) {
    "exercise" -> r.value<Exercise>().name
    "program" -> r.value<TrainingProgram>().name
    "session" -> r.value<WorkoutSession>().let { "${it.dayName} ${date(it.started)}" }
    "weight" -> r.value<BodyWeightEntry>().let { "Punnitus ${date(it.time)}" }
    else -> "Asetukset"
} + if (r.deleted) " (poistettu)" else ""
@Composable fun SettingsScreen(vm: SaliViewModel, settings: Settings) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ImportPreview?>(null) }; var choices by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { busy = true; vm.run {
            try {
                val text = vm.repo.export(); require(text.toByteArray().size <= MAX_BACKUP_BYTES) { "Varmuuskopio ylittää 32 Mt rajan" }
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                context.preferences.edit { it[longPreferencesKey("last_export")] = System.currentTimeMillis() }
                withContext(Dispatchers.Main) { message = "Varmuuskopio tallennettu." }
            } finally { withContext(Dispatchers.Main) { busy = false } }
        } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; vm.run {
            try {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytesLimited(MAX_BACKUP_BYTES).toString(Charsets.UTF_8) }
                val backup = parseBackup(text); val baseline = vm.repo.all(); val items = preview(baseline, backup.records)
                withContext(Dispatchers.Main) { pending = ImportPreview(baseline, backup, items); choices = emptyMap(); message = "" }
            } finally { withContext(Dispatchers.Main) { busy = false } }
        } }
    }
    Page("Asetukset") {
        Row { Switch(settings.dark, { v -> vm.run { vm.repo.settings { it.copy(dark = v) } } }); Text("Tumma teema", Modifier.padding(12.dp)) }
        Row { Switch(settings.keepScreen, { v -> vm.run { vm.repo.settings { it.copy(keepScreen = v) } } }); Text("Pidä näyttö päällä treenissä", Modifier.padding(12.dp)) }
        Text("Tiedot ovat vain tässä laitteessa. Ota varmuuskopio säännöllisesti ja ennen sovelluksen poistamista.")
        Action("Vie JSON-varmuuskopio", !busy) { export.launch("OMA-SALI-varmuuskopio.json") }
        Action("Yhdistä JSON-varmuuskopio", !busy) { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotEmpty()) Text(message)
        pending?.let { p ->
            Text("Palautuksen esikatselu", style = MaterialTheme.typography.titleLarge)
            Text("Lisätään ${p.items.count { it.type == MergeType.ADD }}, päivitetään ${p.items.count { it.type == MergeType.UPDATE }}, ristiriitoja ${p.items.count { it.type == MergeType.CONFLICT }}. Samat ja vanhemmat versiot ohitetaan.")
            p.items.filter { it.type in listOf(MergeType.ADD, MergeType.UPDATE, MergeType.CONFLICT) }.forEach { item -> CardBlock {
                Text(recordLabel(item.incoming))
                Text(when (item.type) { MergeType.ADD -> "Uusi tietue"; MergeType.UPDATE -> "Päivitetään"; else -> "Ristiriita: valitse säilytettävä versio" })
                if (item.type == MergeType.CONFLICT) {
                    Text("Nykyinen: ${recordLabel(item.current!!)}")
                    Text("Varmuuskopio: ${recordLabel(item.incoming)}")
                    var details by remember(item.incoming.id) { mutableStateOf(false) }
                    TextButton(onClick = { details = !details }) { Text("Näytä versioiden tiedot") }
                    if (details) { Text("Nykyinen:\n${item.current.payload}"); Text("Varmuuskopio:\n${item.incoming.payload}") }
                    Row { FilterChip(choices[item.incoming.id] == false, { choices = choices + (item.incoming.id to false) }, label = { Text("Nykyinen") }); Spacer(Modifier.width(8.dp)); FilterChip(choices[item.incoming.id] == true, { choices = choices + (item.incoming.id to true) }, label = { Text("Varmuuskopio") }) }
                }
            } }
            Action("Vahvista yhdistäminen", !busy && p.items.filter { it.type == MergeType.CONFLICT }.all { it.incoming.id in choices }) {
                busy = true
                vm.run { try { vm.repo.merge(p.baseline, p.backup, choices); withContext(Dispatchers.Main) { pending = null; message = "Varmuuskopio yhdistetty turvallisesti." } } finally { withContext(Dispatchers.Main) { busy = false } } }
            }
            TextButton(onClick = { pending = null }, enabled = !busy) { Text("Peruuta palautus") }
        }
        Text("OMA SALI · offline · ei mainoksia tai analytiikkaa")
    }
}
