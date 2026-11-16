package fi.omasali.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun Home(vm: SaliViewModel, rows: List<Record>, settings: Settings, open: (String) -> Unit) {
    val programs = rows.values<TrainingProgram>("program")
    val sessions = rows.values<WorkoutSession>("session")
    val active = programs.find { it.id == settings.activeProgram } ?: programs.firstOrNull()
    var choose by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    fun start(p: TrainingProgram, d: ProgramDay) {
        if (starting) return
        starting = true
        vm.run { try { val sid = vm.repo.start(p.id, d.id); withContext(Dispatchers.Main) { open(sid) } } finally { withContext(Dispatchers.Main) { starting = false } } }
    }
    Page("OMA SALI") {
        Text("Tervetuloa treenaamaan!", style = MaterialTheme.typography.titleMedium)
        if (active == null) Text("Luo ensin ohjelma Ohjelmat-välilehdellä.")
        active?.let { p -> CardBlock {
            Text("Aktiivinen ohjelma", style = MaterialTheme.typography.labelMedium)
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            val next = nextDay(p, sessions)
            Text("Seuraava treeni", style = MaterialTheme.typography.labelMedium)
            Text(next?.name ?: "Lisää ohjelmaan treenipäivä", style = MaterialTheme.typography.headlineSmall)
            Action("▶  Aloita treeni", !starting && next != null && next.exercises.isNotEmpty()) { next?.let { start(p, it) } }
            OutlinedButton(onClick = { choose = true }, modifier = Modifier.fillMaxWidth(), enabled = !starting && p.days.isNotEmpty()) { Text("Valitse toinen treenipäivä") }
        } }
        val ongoing = sessions.filter { it.status == Status.ACTIVE }.sortedByDescending { it.started }
        Text("Keskeneräiset treenit · ${ongoing.size}", style = MaterialTheme.typography.titleMedium)
        if (ongoing.isEmpty()) Text("Ei keskeneräisiä treenejä.", style = MaterialTheme.typography.bodySmall)
        ongoing.forEach { s -> CompactRow(s.dayName, "${s.exercises.count(::isComplete)}/${s.exercises.size} liikettä · ${date(s.started)}", "↗", { open(s.id) }) { TextButton(onClick = { open(s.id) }) { Text("Jatka") } } }
        sessions.filter { it.status == Status.FINISHED }.maxByOrNull { it.ended ?: it.started }?.let { last ->
            Text("Edellinen treeni", style = MaterialTheme.typography.titleMedium)
            Surface(modifier = Modifier.clickable { open(last.id) }) { CardBlock {
                Text(last.dayName, style = MaterialTheme.typography.titleLarge)
                Text("${last.programName} · ${date(last.started)}", style = MaterialTheme.typography.bodySmall)
                Text("${last.exercises.count(::isComplete)} tehtyä liikettä  ·  ${last.exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { it.done } }} sarjaa")
                newRecordSummary(last, sessions)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            } }
        }
    }
    if (choose && active != null) AlertDialog(onDismissRequest = { choose = false }, title = { Text("Valitse treenipäivä") }, text = {
        Column { ChoiceField("Päivä", "Valitse aloitettava päivä", active.days.filter { it.exercises.isNotEmpty() }.map { it.id to it.name }) { id -> choose = false; start(active, active.days.first { it.id == id }) } }
    }, confirmButton = { TextButton(onClick = { choose = false }) { Text("Sulje") } })
}
