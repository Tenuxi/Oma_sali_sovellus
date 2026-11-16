package fi.omasali.app

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.platform.testTag
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
    val summary = summarize(s)
    val complete = summary.completedExercises
    val skipped = summary.skippedExercises
    val sections = workoutSections(s, opened)
    var targetEdit by remember { mutableStateOf<WorkoutExercise?>(null) }
    fun move(exerciseId: String, delta: Int) = vm.session(s.id) { old -> val i = old.exercises.indexOfFirst { it.id == exerciseId }; if (i < 0) old else old.copy(exercises = old.exercises.move(i, delta)) }
    Page(s.dayName, opened ?: "summary") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.programName} · ${date(s.started)}", style = MaterialTheme.typography.bodySmall)
                Text(if (s.status == Status.ACTIVE) "Kesken · tallentuu automaattisesti" else if (s.status == Status.CANCELLED) "Peruttu" else "Päättynyt treeni · voit muokata suorituksia", style = MaterialTheme.typography.bodySmall)
            }
            MoreMenu("Treenin toiminnot", listOf(MenuAction("Ajankohta ja muistiinpanot") { info = true }, MenuAction("Treenin yhteenveto") { vm.openExercise(s.id, null) }))
        }
        Text("$complete/${summary.totalExercises} liikettä valmiina · ${summary.completedSets}/${summary.totalSets} sarjaa tehty", style = MaterialTheme.typography.labelLarge)
        if (skipped > 0) Text("$skipped liikettä ohitettu", style = MaterialTheme.typography.bodySmall)
        WorkoutProgress(summary)
        if (s.status != Status.CANCELLED) {
            val active = sections.active
            active?.let { ex ->
                Card(Modifier.fillMaxWidth().testTag("active-exercise-${ex.id}")) { Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.exercises.indexOfFirst { it.id == ex.id } + 1}. ${ex.exercise.name}", style = MaterialTheme.typography.titleMedium)
                            Text("Tavoite ${ex.target.sets} × ${ex.target.low}–${ex.target.high}", style = MaterialTheme.typography.bodySmall)
                            if (isComplete(ex)) Text("✓ Tavoitesarjat tehty", color = MaterialTheme.colorScheme.primary)
                        }
                        val index = s.exercises.indexOfFirst { it.id == ex.id }
                        MoreMenu("${ex.exercise.name}: toiminnot", listOf(
                            MenuAction("Huomiot") { notes = ex }, MenuAction("Muokkaa sarjatavoitetta") { targetEdit = ex }, MenuAction("Vaihda liike") { replace = ex.id },
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
                        SetHeader(ex.exercise.load)
                        ex.sets.forEachIndexed { i, set -> key(set.id) {
                            SetEditor(set, ex.exercise.load, i + 1, vm.draftFor(set, ex.exercise.load), { draft -> vm.editSet(s.id, ex.id, set, ex.exercise.load, draft) }, {
                                vm.exercise(s.id, ex.id) { e -> e.copy(sets = e.sets.filterNot { it.id == set.id }) }
                                vm.updateUi { it.copy(drafts = it.drafts - set.id) }
                            }, if (vm.hasOlderDraft(set, ex.exercise.load)) ({ vm.ui.drafts[set.id]?.let { vm.editSet(s.id, ex.id, set, ex.exercise.load, it) } }) else null)
                        } }
                        TextButton(onClick = { vm.exercise(s.id, ex.id) { it.copy(sets = it.sets + WorkoutSet(body = it.sets.lastOrNull()?.body)) } }) { Text("＋ Lisää sarja") }
                        suggestion(ex)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    val next = nextOpen(s, ex.id)
                    if (next != null) Action("Seuraava liike →") { vm.openExercise(s.id, next) }
                } }
            }
            if (active == null) CardBlock {
                Text("Treenin yhteenveto", style = MaterialTheme.typography.titleLarge)
                Text("$complete tehtyä liikettä · $skipped ohitettua · ${summary.completedSets} tehtyä sarjaa")
                val remaining = s.exercises.count { !it.skipped && !isComplete(it) }
                if (remaining > 0) Text("$remaining liikettä on vielä kesken. Voit avata ne alta tai lopettaa treenin kesken.")
                if (s.notes.isNotBlank()) Text(s.notes)
            }
            val pending = sections.pending
            if (pending.isNotEmpty()) Text("Muut liikkeet", style = MaterialTheme.typography.titleMedium)
            pending.forEach { ex -> ClosedExercise(ex, s.exercises.indexOfFirst { it.id == ex.id } + 1) { vm.openExercise(s.id, ex.id) } }
            val done = sections.complete
            if (done.isNotEmpty()) Text("Tehdyt liikkeet", style = MaterialTheme.typography.titleMedium)
            done.forEach { ex -> ClosedExercise(ex, s.exercises.indexOfFirst { it.id == ex.id } + 1) { vm.openExercise(s.id, ex.id) } }
            val skippedExercises = sections.skipped
            if (skippedExercises.isNotEmpty()) Text("Ohitetut liikkeet", style = MaterialTheme.typography.titleMedium)
            skippedExercises.forEach { ex -> ClosedExercise(ex, s.exercises.indexOfFirst { it.id == ex.id } + 1) { vm.openExercise(s.id, ex.id) } }
            TextButton(onClick = { add = true }) { Text("＋ Lisää liike treeniin") }
            if (s.status == Status.ACTIVE) {
                Action("Lopeta treeni") { vm.session(s.id) { it.copy(status = Status.FINISHED, ended = maxOf(System.currentTimeMillis(), it.started)) } }
                TextButton(onClick = { cancel = true }) { Text("Peru aloitus") }
            } else TextButton(onClick = { vm.session(s.id) { it.copy(status = Status.ACTIVE, ended = null) } }) { Text("Jatka treeniä keskeneräisenä") }
        }
    }
    targetEdit?.let { ex -> EditDialog("Tämän treenin sarjatavoite", listOf("Tavoitesarjojen määrä"), listOf("${ex.target.sets}"), { targetEdit = null }) { values ->
        val count = values[0].toInt(); require(count in 1..30)
        vm.exercise(s.id, ex.id) { it.copy(target = it.target.copy(sets = count)) }
    } }
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
@Composable private fun ClosedExercise(ex: WorkoutExercise, position: Int, open: () -> Unit) {
    val state = if (ex.skipped) "Ohitettu" else if (isComplete(ex)) "Tehty" else "${ex.sets.count { it.done }}/${maxOf(ex.target.sets, ex.sets.size)} sarjaa"
    CompactRow("$position. ${ex.exercise.name}", "${ex.target.sets} × ${ex.target.low}–${ex.target.high} · $state", if (isComplete(ex)) "✓" else if (ex.skipped) "−" else "↔", open) { IconButton(onClick = open, modifier = Modifier.semantics { contentDescription = "Avaa ${ex.exercise.name}" }) { Text("⌄") } }
}
@Composable fun SetEditor(set: WorkoutSet, load: Load, number: Int, draft: SetDraft, save: (SetDraft) -> Unit, delete: () -> Unit, restoreOlder: (() -> Unit)? = null) {
    val invalid = runCatching { draft.applyTo(set, load) }.isFailure
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$number", Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall)
            CompactNumber(if (load == Load.EXTERNAL) draft.weight else draft.body, "Sarja $number: ${if (load == Load.EXTERNAL) "paino" else "kehonpaino"} kg", Modifier.weight(1f)) { save(if (load == Load.EXTERNAL) draft.copy(weight = it) else draft.copy(body = it)) }
            CompactNumber(draft.reps, "Sarja $number: toistot", Modifier.weight(1f), integer = true) { save(draft.copy(reps = it)) }
            Checkbox(draft.done, { value -> save(draft.copy(done = value)) }, modifier = Modifier.semantics { contentDescription = "Sarja $number tehty" })
            MoreMenu("Sarja $number: toiminnot", listOfNotNull(MenuAction("Poista sarja") { delete() }, restoreOlder?.let { MenuAction("Palauta aiempi syöte") { it() } }))
        }
        if (load == Load.ASSISTED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text("Lisäpaino kg", style = MaterialTheme.typography.labelSmall); CompactNumber(draft.added, "Sarja $number: lisäpaino kg", Modifier.fillMaxWidth()) { save(draft.copy(added = it)) } }
            Column(Modifier.weight(1f)) { Text("Avustus kg", style = MaterialTheme.typography.labelSmall); CompactNumber(draft.assistance, "Sarja $number: avustus kg", Modifier.fillMaxWidth()) { save(draft.copy(assistance = it)) } }
        }
        if (restoreOlder != null) Text("Aiempi syöte tallessa sarjavalikossa.", style = MaterialTheme.typography.labelSmall)
        if (!invalid && draft.applyTo(set, load) != set) Text("Tallennetaan sarjaa…", style = MaterialTheme.typography.labelSmall)
        if (invalid) Text("Keskeneräinen syöte säilytetty. Tarkista luvut; käytä joko lisäpainoa tai avustusta. Tehty sarja vaatii toistoja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
@Composable private fun CompactNumber(value: String, label: String, modifier: Modifier, integer: Boolean = false, change: (String) -> Unit) {
    CompactInput(value, label, modifier, keyboard = if (integer) KeyboardType.Number else KeyboardType.Decimal, change = change)
}
@Composable private fun SetHeader(load: Load) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("#", Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall)
        Text(if (load == Load.EXTERNAL) "Paino (kg)" else "Keho (kg)", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text("Toistot", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text("✓", Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(48.dp))
    }
}
@Composable fun WorkoutProgress(summary: WorkoutSummary) {
    // No terminal marker: at zero there must be no green segment or green dot.
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)).testTag("workout-progress").semantics { progressBarRangeInfo = ProgressBarRangeInfo(summary.progress, 0f..1f) }) {
        if (summary.progress > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(summary.progress).background(MaterialTheme.colorScheme.primary))
    }
}
