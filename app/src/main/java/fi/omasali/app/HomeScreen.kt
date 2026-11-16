package fi.omasali.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        Text("Tervetuloa treenaamaan!", style = MaterialTheme.typography.bodyMedium)
        if (active == null) Text("Luo ensin ohjelma Ohjelmat-välilehdellä.")
        active?.let { p -> CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aktiivinen ohjelma", style = MaterialTheme.typography.labelSmall)
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                }
                Text("Aktiivinen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            val next = nextDay(p, sessions)
            Text("Seuraava treeni", style = MaterialTheme.typography.labelSmall)
            Text(next?.name ?: "Lisää ohjelmaan treenipäivä", style = MaterialTheme.typography.titleLarge)
            Action("▶  Aloita treeni", !starting && next != null && next.exercises.isNotEmpty()) { next?.let { start(p, it) } }
            TextButton(onClick = { choose = true }, modifier = Modifier.fillMaxWidth(), enabled = !starting && p.days.isNotEmpty()) { Text("Valitse toinen treenipäivä", style = MaterialTheme.typography.bodySmall) }
        } }
        val ongoing = sessions.filter { it.status == Status.ACTIVE }.sortedByDescending { it.started }
        Text("Keskeneräiset treenit · ${ongoing.size}", style = MaterialTheme.typography.titleSmall)
        if (ongoing.isEmpty()) Text("Ei keskeneräisiä treenejä.", style = MaterialTheme.typography.bodySmall)
        ongoing.forEach { s ->
            val summary = summarize(s)
            CompactRow(s.dayName, "${summary.completedExercises}/${summary.totalExercises} liikettä · ${summary.completedSets} sarjaa tehty", "↗", { open(s.id) }) { TextButton(onClick = { open(s.id) }) { Text("Jatka") } }
        }
        latestFinished(sessions)?.let { last ->
            val summary = summarize(last)
            Surface(modifier = Modifier.clickable { open(last.id) }.testTag("last-workout-${last.id}")) { CardBlock {
                Text("Edellinen treeni", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(last.dayName, style = MaterialTheme.typography.titleMedium)
                        Text("${last.programName} · ${date(last.started)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
                Text("${summary.completedExercises}/${summary.totalExercises} liikettä valmiina · ${summary.completedSets} sarjaa tehty", Modifier.testTag("last-workout-counts"), style = MaterialTheme.typography.bodyMedium)
                if (summary.completedExercises + summary.skippedExercises < summary.totalExercises) Text("Treeni päätetty osittain tehtynä", style = MaterialTheme.typography.labelSmall)
                newRecordSummary(last, sessions)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            } }
        }
    }
    if (choose && active != null) AlertDialog(onDismissRequest = { choose = false }, title = { Text("Valitse treenipäivä") }, text = {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            active.days.forEach { day -> TextButton(onClick = { choose = false; start(active, day) }, enabled = day.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(day.name) } }
        }
    }, confirmButton = { TextButton(onClick = { choose = false }) { Text("Sulje") } })
}
