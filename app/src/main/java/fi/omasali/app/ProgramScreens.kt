package fi.omasali.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

@Composable fun ExerciseLibrary(vm: SaliViewModel, rows: List<Record>) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<Exercise?>(null) }
    var details by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    val exercises = rows.values<Exercise>("exercise")
    CompactInput(query, "Hae liikettä…", Modifier.fillMaxWidth()) { query = it }
    ChipStrip {
        (listOf("") + exercises.map { it.muscle }.distinct().sorted()).forEach { m -> FilterChip(muscle == m, { muscle = m }, label = { Text(m.ifEmpty { "Kaikki" }) }) }
    }
    TextButton(onClick = { edit = Exercise(name = "") }) { Text("＋ Lisää oma liike") }
    val filtered = exercises.filter { it.name.contains(query, true) && (muscle.isEmpty() || it.muscle == muscle) }.sortedBy { it.name }
    if (filtered.isEmpty()) Text("Ei hakua vastaavia liikkeitä.")
    filtered.forEach { e -> key(e.id) {
        CompactRow(e.name, "${e.muscle} · ${loadLabel(e.load)}", "↔", { details = e }) {
            MoreMenu("${e.name}: toiminnot", listOf(MenuAction("Muokkaa") { edit = e }, MenuAction("Poista") { deleting = e }))
        }
    } }
    details?.let { e -> AlertDialog(onDismissRequest = { details = null }, title = { Text(e.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Photo(e.photo); Text("${e.muscle} · ${e.equipment}"); Text(loadLabel(e.load)); Text("Painonkorotus ${kg(e.increment)} kg"); Text(e.notes)
        }
    }, confirmButton = { TextButton(onClick = { details = null; edit = e }) { Text("Muokkaa") } }, dismissButton = { TextButton(onClick = { details = null }) { Text("Sulje") } }) }
    deleting?.let { e -> ConfirmDialog("Poista ${e.name}?", "Poista liike ensin ohjelmien päivistä. Historialliset suoritukset ja kuvat säilyvät.", { deleting = null }) { vm.run { vm.repo.delete(e.id) } } }
    edit?.let { e -> ExerciseDialog(e, { edit = null }) { updated -> vm.save(record(updated.id, "exercise", updated)); edit = null } }
}

@Composable fun ProgramEditor(vm: SaliViewModel, rows: List<Record>, id: String) {
    val p = rows.find { it.id == id && !it.deleted }?.value<TrainingProgram>() ?: return
    var selected by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val day = p.days.find { it.id == selected } ?: p.days.firstOrNull()
    var rename by remember { mutableStateOf(false) }
    var dayEdit by remember { mutableStateOf<ProgramDay?>(null) }
    var addTo by remember { mutableStateOf<String?>(null) }
    var targetEdit by remember { mutableStateOf<Pair<String, ProgramExercise>?>(null) }
    var removeDay by remember { mutableStateOf<ProgramDay?>(null) }
    fun moveExercise(dayId: String, exerciseId: String, delta: Int) {
        vm.program(id) { old -> old.copy(days = old.days.map { d -> if (d.id != dayId) d else {
            val index = d.exercises.indexOfFirst { it.id == exerciseId }
            if (index < 0) d else d.copy(exercises = d.exercises.move(index, delta))
        } }) }
    }
    Page("Muokkaa ohjelmaa") {
        Row {
            Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MoreMenu("Ohjelman toiminnot", listOf(MenuAction("Nimeä ohjelma") { rename = true }, MenuAction("Lisää päivä") { dayEdit = ProgramDay(name = "") }))
        }
        if (p.days.isEmpty()) Action("Lisää ensimmäinen treenipäivä") { dayEdit = ProgramDay(name = "") }
        ChipStrip { p.days.forEach { d -> FilterChip(day?.id == d.id, { selected = d.id }, label = { Text(d.name) }) } }
        day?.let { d ->
            Row {
                Text("${d.exercises.size} liikettä", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                val index = p.days.indexOfFirst { it.id == d.id }
                MoreMenu("Päivän toiminnot", listOf(
                    MenuAction("Nimeä päivä") { dayEdit = d },
                    MenuAction("Siirrä päivää vasemmalle", index > 0) { vm.program(id) { it.copy(days = it.days.move(it.days.indexOfFirst { day -> day.id == d.id }, -1)) } },
                    MenuAction("Siirrä päivää oikealle", index < p.days.lastIndex) { vm.program(id) { it.copy(days = it.days.move(it.days.indexOfFirst { day -> day.id == d.id }, 1)) } },
                    MenuAction("Lisää päivä") { dayEdit = ProgramDay(name = "") },
                    MenuAction("Poista päivä") { removeDay = d }
                ))
            }
            d.exercises.forEachIndexed { i, target -> key(target.id) {
                val exercise = rows.find { it.id == target.exerciseId }?.value<Exercise>()
                CompactRow(exercise?.name ?: "Poistettu liike", "${target.sets} × ${target.low}–${target.high}" + if (target.notes.isBlank()) "" else " · ${target.notes}", "${i + 1}", { targetEdit = d.id to target }) {
                    MoreMenu("${exercise?.name}: toiminnot", listOf(
                        MenuAction("Muokkaa tavoitteita") { targetEdit = d.id to target },
                        MenuAction("Siirrä ylös", i > 0) { moveExercise(d.id, target.id, -1) },
                        MenuAction("Siirrä alas", i < d.exercises.lastIndex) { moveExercise(d.id, target.id, 1) },
                        MenuAction("Poista päivästä") { vm.program(id) { old -> old.copy(days = old.days.map { if (it.id == d.id) it.copy(exercises = it.exercises.filterNot { t -> t.id == target.id }) else it }) } }
                    ))
                }
            } }
            Action("＋ Lisää liike") { addTo = d.id }
        }
    }
    if (rename) EditDialog("Ohjelman nimi", listOf("Nimi"), listOf(p.name), { rename = false }) { v -> require(v[0].isNotBlank()); vm.program(id) { it.copy(name = v[0]) } }
    dayEdit?.let { d -> EditDialog("Treenipäivä", listOf("Nimi"), listOf(d.name), { dayEdit = null }) { v ->
        require(v[0].isNotBlank()); selected = d.id
        vm.program(id) { old -> old.copy(days = if (old.days.any { it.id == d.id }) old.days.map { if (it.id == d.id) it.copy(name = v[0]) else it } else old.days + d.copy(name = v[0])) }
    } }
    removeDay?.let { d -> ConfirmDialog("Poista päivä?", "${d.name} poistetaan ohjelmasta. Treenihistoria säilyy.", { removeDay = null }) { vm.program(id) { it.copy(days = it.days.filterNot { day -> day.id == d.id }) } } }
    addTo?.let { dayId -> PickExercise(rows, { addTo = null }) { e -> vm.program(id) { old -> old.copy(days = old.days.map { if (it.id == dayId) it.copy(exercises = it.exercises + ProgramExercise(exerciseId = e.id)) else it }) } } }
    targetEdit?.let { (dayId, t) -> EditDialog("Sarjatavoite", listOf("Sarjat", "Toistot vähintään", "Toistot enintään", "Huomiot"), listOf("${t.sets}", "${t.low}", "${t.high}", t.notes), { targetEdit = null }) { v ->
        val updated = t.copy(sets = v[0].toInt(), low = v[1].toInt(), high = v[2].toInt(), notes = v[3]); require(updated.sets in 1..30 && updated.low in 1..1000 && updated.high in updated.low..1000)
        vm.program(id) { old -> old.copy(days = old.days.map { d -> if (d.id == dayId) d.copy(exercises = d.exercises.map { if (it.id == t.id) updated else it }) else d }) }
    } }
}
