package fi.omasali.app

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable fun WorkoutEditor(vm: SaliViewModel, rows: List<Record>, settings: Settings, sessionId: String) {
    val s = rows.find { it.id == sessionId }?.value<WorkoutSession>() ?: return
    val context = LocalContext.current
    DisposableEffect(settings.keepScreen, s.status) {
        val window = (context as? Activity)?.window
        if (settings.keepScreen && s.status == Status.ACTIVE) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val opened = initialOpen(s, vm.ui.opened[s.id])
    // Pin the initial choice too, so completing the last set doesn't collapse the user's editor.
    LaunchedEffect(s.id) { if (s.id !in vm.ui.opened) vm.openExercise(s.id, opened) }
    var add by remember { mutableStateOf(false) }; var replace by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(false) }; var notes by remember { mutableStateOf<WorkoutExercise?>(null) }
    var deleting by remember { mutableStateOf<WorkoutExercise?>(null) }
    var cancel by remember { mutableStateOf(false) }
    val complete = s.exercises.count(::isComplete)
    val skipped = s.exercises.count { it.skipped }
    fun move(exerciseId: String, delta: Int) = vm.session(s.id) { old -> val i = old.exercises.indexOfFirst { it.id == exerciseId }; if (i < 0) old else old.copy(exercises = old.exercises.move(i, delta)) }
    Page(s.dayName, opened ?: "summary") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.programName} · ${date(s.started)}", style = MaterialTheme.typography.bodySmall)
                Text(if (s.status == Status.ACTIVE) "Kesken · tallentuu automaattisesti" else if (s.status == Status.CANCELLED) "Peruttu" else "Valmis · voit korjata tietoja", style = MaterialTheme.typography.bodySmall)
            }
            MoreMenu("Treenin toiminnot", listOf(MenuAction("Ajankohta ja muistiinpanot") { info = true }, MenuAction("Treenin yhteenveto") { vm.openExercise(s.id, null) }))
        }
        Text("$complete / ${s.exercises.size} liikettä tehty" + if (skipped > 0) " · $skipped ohitettu" else "", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { if (s.exercises.isEmpty()) 0f else complete.toFloat() / s.exercises.size }, modifier = Modifier.fillMaxWidth())
        if (s.status != Status.CANCELLED) {
            val active = s.exercises.find { it.id == opened }
            active?.let { ex ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.exercise.name, style = MaterialTheme.typography.titleLarge)
                            Text("Tavoite ${ex.target.sets} × ${ex.target.low}–${ex.target.high}", style = MaterialTheme.typography.bodySmall)
                            if (isComplete(ex)) Text("✓ Kaikki sarjat tehty", color = MaterialTheme.colorScheme.primary)
                        }
                        val index = s.exercises.indexOfFirst { it.id == ex.id }
                        MoreMenu("${ex.exercise.name}: toiminnot", listOf(
                            MenuAction("Huomiot") { notes = ex }, MenuAction("Vaihda liike") { replace = ex.id },
                            MenuAction(if (ex.skipped) "Palauta ohitettu liike" else "Ohita liike") { vm.exercise(s.id, ex.id) { it.copy(skipped = !it.skipped) } },
                            MenuAction("Siirrä ylös", index > 0) { move(ex.id, -1) }, MenuAction("Siirrä alas", index < s.exercises.lastIndex) { move(ex.id, 1) },
                            MenuAction("Poista liike treenistä") { deleting = ex }
                        ))
                    }
                    if (ex.target.notes.isNotBlank()) Text(ex.target.notes, style = MaterialTheme.typography.bodySmall)
                    if (ex.notes.isNotBlank()) Text(ex.notes, style = MaterialTheme.typography.bodySmall)
                    val previous = rows.values<WorkoutSession>("session").filter { it.id != s.id && it.status != Status.CANCELLED && it.started <= s.started }.sortedByDescending { it.started }.firstNotNullOfOrNull { session -> session.exercises.firstOrNull { it.exercise.id == ex.exercise.id && it.exercise.load == ex.exercise.load && !it.skipped && it.sets.any { set -> set.done } } }
                    if (previous != null) {
                        Text("Edellinen: " + previous.sets.filter { it.done }.joinToString(" · ") { setSummary(it, previous.exercise.load) }, style = MaterialTheme.typography.bodySmall)
                        suggestion(previous)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    if (ex.skipped) Text("Liike ohitettu. Sarjoja ei lasketa tilastoihin.") else {
                        Text(if (ex.exercise.load == Load.EXTERNAL) "Sarja  ·  paino (kg)  ·  toistot  ·  tehty" else "Sarja  ·  kehonpaino (kg)  ·  toistot  ·  tehty", style = MaterialTheme.typography.labelSmall)
                        ex.sets.forEachIndexed { i, set -> key(set.id) {
                            SetEditor(set, ex.exercise.load, i + 1, vm.ui.drafts[set.id] ?: SetDraft(set), { draft -> vm.editSet(s.id, ex.id, set, ex.exercise.load, draft) }, {
                                vm.exercise(s.id, ex.id) { e -> e.copy(sets = e.sets.filterNot { it.id == set.id }) }
                                vm.updateUi { it.copy(drafts = it.drafts - set.id) }
                            })
                        } }
                        TextButton(onClick = { vm.exercise(s.id, ex.id) { it.copy(sets = it.sets + WorkoutSet(body = it.sets.lastOrNull()?.body)) } }) { Text("＋ Lisää sarja") }
                        suggestion(ex)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    val next = nextOpen(s, ex.id)
                    Action(if (next == null) "Treenin yhteenveto" else "Seuraava liike →") { vm.openExercise(s.id, next) }
                } }
            }
            if (active == null) CardBlock {
                Text("Treenin yhteenveto", style = MaterialTheme.typography.titleLarge)
                Text("$complete tehtyä liikettä · $skipped ohitettua · ${s.exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { it.done } }} tehtyä sarjaa")
                val remaining = s.exercises.count { !it.skipped && !isComplete(it) }
                if (remaining > 0) Text("$remaining liikettä on vielä kesken. Voit avata ne alta tai lopettaa treenin kesken.")
                if (s.notes.isNotBlank()) Text(s.notes)
            }
            val pending = s.exercises.filter { it.id != opened && !it.skipped && !isComplete(it) }
            if (pending.isNotEmpty()) Text("Muut liikkeet", style = MaterialTheme.typography.titleMedium)
            pending.forEach { ex -> ClosedExercise(ex) { vm.openExercise(s.id, ex.id) } }
            val done = s.exercises.filter { it.id != opened && isComplete(it) }
            if (done.isNotEmpty()) Text("Tehdyt liikkeet", style = MaterialTheme.typography.titleMedium)
            done.forEach { ex -> ClosedExercise(ex) { vm.openExercise(s.id, ex.id) } }
            val skippedExercises = s.exercises.filter { it.id != opened && it.skipped }
            if (skippedExercises.isNotEmpty()) Text("Ohitetut liikkeet", style = MaterialTheme.typography.titleMedium)
            skippedExercises.forEach { ex -> ClosedExercise(ex) { vm.openExercise(s.id, ex.id) } }
            TextButton(onClick = { add = true }) { Text("＋ Lisää liike treeniin") }
            if (s.status == Status.ACTIVE) {
                Action("Lopeta treeni") { vm.session(s.id) { it.copy(status = Status.FINISHED, ended = maxOf(System.currentTimeMillis(), it.started)) } }
                TextButton(onClick = { cancel = true }) { Text("Peru aloitus") }
            } else Action("Avaa uudelleen keskeneräiseksi") { vm.session(s.id) { it.copy(status = Status.ACTIVE, ended = null) } }
        }
    }
    if (cancel) ConfirmDialog("Peru aloitus?", "Treeni merkitään perutuksi ja sen vaikutus kiertoon poistuu. Myöhemmät aloitukset säilyvät.", { cancel = false }) { vm.session(s.id) { it.copy(status = Status.CANCELLED, ended = null) } }
    deleting?.let { ex -> ConfirmDialog("Poista liike treenistä?", "${ex.exercise.name} ja sen sarjat poistetaan tästä treenistä. Ohjelma ei muutu.", { deleting = null }) { vm.session(s.id) { it.copy(exercises = it.exercises.filterNot { e -> e.id == ex.id }) } } }
    if (add || replace != null) PickExercise(rows, { add = false; replace = null }) { e ->
        val replaceId = replace
        val target = ProgramExercise(exerciseId = e.id)
        val new = WorkoutExercise(exercise = e, target = target, sets = List(target.sets) { WorkoutSet() })
        vm.session(s.id) { old -> old.copy(exercises = if (replaceId == null) old.exercises + new else old.exercises.map { if (it.id == replaceId) new else it }) }
        vm.openExercise(s.id, new.id)
    }
    if (info) EditDialog("Treenin tiedot", listOf("Alku (pp.kk.vvvv tt:mm)", "Loppu (tyhjä = kesken)", "Muistiinpanot"), listOf(date(s.started), s.ended?.let(::date) ?: "", s.notes), { info = false }) { v ->
        val start = timestamp(v[0]); val end = v[1].takeIf { it.isNotBlank() }?.let(::timestamp); require(end == null || end >= start) { "Loppu on ennen alkua" }
        vm.session(s.id) { it.copy(started = start, ended = end, notes = v[2], status = if (it.status == Status.CANCELLED) Status.CANCELLED else if (end == null) Status.ACTIVE else Status.FINISHED) }
    }
    notes?.let { e -> EditDialog("Liikkeen huomiot", listOf("Huomiot"), listOf(e.notes), { notes = null }) { v -> vm.exercise(s.id, e.id) { it.copy(notes = v[0]) } } }
}
@Composable private fun ClosedExercise(ex: WorkoutExercise, open: () -> Unit) {
    val state = if (ex.skipped) "Ohitettu" else if (isComplete(ex)) "Tehty" else "${ex.sets.count { it.done }}/${ex.sets.size} sarjaa"
    CompactRow(ex.exercise.name, "${ex.target.sets} × ${ex.target.low}–${ex.target.high} · $state", if (isComplete(ex)) "✓" else if (ex.skipped) "−" else "↔", open) { IconButton(onClick = open, modifier = Modifier.semantics { contentDescription = "Avaa ${ex.exercise.name}" }) { Text("⌄") } }
}
@Composable fun SetEditor(set: WorkoutSet, load: Load, number: Int, draft: SetDraft, save: (SetDraft) -> Unit, delete: () -> Unit) {
    val invalid = runCatching { draft.applyTo(set, load) }.isFailure
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$number", Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall)
            CompactNumber(if (load == Load.EXTERNAL) draft.weight else draft.body, "Sarja $number: ${if (load == Load.EXTERNAL) "paino" else "kehonpaino"} kg", Modifier.weight(1f)) { save(if (load == Load.EXTERNAL) draft.copy(weight = it) else draft.copy(body = it)) }
            CompactNumber(draft.reps, "Sarja $number: toistot", Modifier.weight(1f), integer = true) { save(draft.copy(reps = it)) }
            Checkbox(draft.done, { value -> save(draft.copy(done = value)) }, modifier = Modifier.semantics { contentDescription = "Sarja $number tehty" })
            MoreMenu("Sarja $number: toiminnot", listOf(MenuAction("Poista sarja") { delete() }))
        }
        if (load == Load.ASSISTED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text("Lisäpaino kg", style = MaterialTheme.typography.labelSmall); CompactNumber(draft.added, "Sarja $number: lisäpaino kg", Modifier.fillMaxWidth()) { save(draft.copy(added = it)) } }
            Column(Modifier.weight(1f)) { Text("Avustus kg", style = MaterialTheme.typography.labelSmall); CompactNumber(draft.assistance, "Sarja $number: avustus kg", Modifier.fillMaxWidth()) { save(draft.copy(assistance = it)) } }
        }
        if (invalid) Text("Keskeneräinen syöte säilytetty. Tarkista luvut; käytä joko lisäpainoa tai avustusta. Tehty sarja vaatii toistoja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
@Composable private fun CompactNumber(value: String, label: String, modifier: Modifier, integer: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, change, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = label }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal))
}
