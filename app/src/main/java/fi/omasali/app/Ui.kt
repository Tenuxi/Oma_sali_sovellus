package fi.omasali.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Base64

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
fun date(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(dateFormat)
fun timestamp(s: String) = LocalDateTime.parse(s, dateFormat).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
inline fun <reified T> List<Record>.values(kind: String): List<T> = filter { it.kind == kind && !it.deleted }.map { it.value<T>() }
fun <T> List<T>.move(index: Int, delta: Int): List<T> = toMutableList().apply { val to = (index + delta).coerceIn(indices); if (to != index) add(to, removeAt(index)) }

@Composable fun SaliApp(vm: SaliViewModel, rows: List<Record>, settings: Settings) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tabs = listOf("home" to "Etusivu", "workouts" to "Treenit", "programs" to "Ohjelmat", "stats" to "Kehitys", "settings" to "Asetukset")
    Scaffold(bottomBar = {
        NavigationBar { tabs.forEachIndexed { i, (key, title) -> NavigationBarItem(selected = route == key, onClick = { nav.navigate(key) { popUpTo("home"); launchSingleTop = true } }, icon = { Text(listOf("⌂", "●", "≡", "↗", "⚙")[i]) }, label = { Text(title, maxLines = 1) }) } }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Home(vm, rows, settings) { nav.navigate("session/$it") } }
                composable("workouts") { Workouts(rows) { nav.navigate("session/$it") } }
                composable("programs") { Programs(vm, rows, settings) { nav.navigate("program/$it") } }
                composable("program/{id}") { e -> ProgramEditor(vm, rows, e.arguments?.getString("id")!!) }
                composable("session/{id}") { e -> WorkoutEditor(vm, rows, settings, e.arguments?.getString("id")!!) }
                composable("stats") { Statistics(vm, rows) }
                composable("settings") { SettingsScreen(vm, settings) }
            }
        }
    }
}
@Composable fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 800.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) { Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) } }
@Composable fun Confirm(label: String, message: String, action: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(label) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { action(); open = false }) { Text("Vahvista") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Takaisin") } })
}
@Composable fun EditDialog(title: String, labels: List<String>, initial: List<String>, close: () -> Unit, save: (List<String>) -> Unit) {
    var fields by remember { mutableStateOf(initial) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label -> OutlinedTextField(value = fields[i], onValueChange = { v -> fields = fields.mapIndexed { j, old -> if (j == i) v else old } }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { try { save(fields); close() } catch (e: Exception) { error = e.message ?: "Tarkista kentät" } }) { Text("Tallenna") } }, dismissButton = { TextButton(onClick = close) { Text("Peruuta") } })
}
@Composable fun Home(vm: SaliViewModel, rows: List<Record>, settings: Settings, open: (String) -> Unit) {
    val programs = rows.values<TrainingProgram>("program")
    val sessions = rows.values<WorkoutSession>("session")
    val active = programs.find { it.id == settings.activeProgram } ?: programs.firstOrNull()
    Page("OMA SALI") {
        Text("Oma tahti. Oma kehitys.", style = MaterialTheme.typography.titleMedium)
        if (active == null) Text("Luo ensin ohjelma Ohjelmat-välilehdellä.")
        active?.let { p ->
            CardBlock {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                val next = nextDay(p, sessions)
                Text("Seuraava treeni: ${next?.name ?: "Lisää ohjelmaan treenipäivä"}")
                p.days.forEach { day -> Action(if (day.id == next?.id) "Aloita ${day.name} · seuraava" else "Aloita ${day.name}", day.exercises.isNotEmpty()) {
                    vm.run { val sid = vm.repo.start(p.id, day.id); withContext(Dispatchers.Main) { open(sid) } }
                } }
            }
        }
        Text("Keskeneräiset", style = MaterialTheme.typography.titleLarge)
        val ongoing = sessions.filter { it.status == Status.ACTIVE }.sortedByDescending { it.started }
        if (ongoing.isEmpty()) Text("Ei keskeneräisiä treenejä.")
        ongoing.forEach { s -> Action("Jatka: ${s.dayName} · ${date(s.started)}") { open(s.id) } }
    }
}
@Composable fun Workouts(rows: List<Record>, open: (String) -> Unit) {
    Page("Treenit") {
        val sessions = rows.values<WorkoutSession>("session").filter { it.status != Status.CANCELLED }.sortedByDescending { it.started }
        if (sessions.isEmpty()) Text("Aloita ensimmäinen treeni etusivulta.")
        sessions.forEach { s -> CardBlock {
            Text("${s.programName} · ${s.dayName}", style = MaterialTheme.typography.titleMedium)
            Text("${date(s.started)} · ${if (s.status == Status.ACTIVE) "Kesken" else "Valmis"}")
            Text("${s.exercises.sumOf { e -> e.sets.count { it.done && !e.skipped } }} sarjaa · ${kg(s.exercises.sumOf(::volume))} kg volyymi")
            Action("Avaa ja muokkaa") { open(s.id) }
        } }
    }
}
@Composable fun Programs(vm: SaliViewModel, rows: List<Record>, settings: Settings, open: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }; var library by rememberSaveable { mutableStateOf(false) }
    Page("Ohjelmat ja liikkeet") {
        Row { FilterChip(selected = !library, onClick = { library = false }, label = { Text("Ohjelmat") }); Spacer(Modifier.width(12.dp)); FilterChip(selected = library, onClick = { library = true }, label = { Text("Liikekirjasto") }) }
        if (library) ExerciseLibrary(vm, rows) else {
            Action("Uusi oma ohjelma") { create = true }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { vm.run { vm.repo.template(2) } }) { Text("2-jakoinen") }; Button(onClick = { vm.run { vm.repo.template(3) } }) { Text("3-jakoinen") } }
            rows.values<TrainingProgram>("program").forEach { p -> CardBlock {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text("${p.days.size} treenipäivää")
                Action("Muokkaa") { open(p.id) }
                TextButton(onClick = { vm.run { vm.repo.settings { it.copy(activeProgram = p.id) } } }) { Text(if (settings.activeProgram == p.id) "✓ Aktiivinen ohjelma" else "Aseta aktiiviseksi") }
                Confirm("Poista ohjelma", "Treenihistoria säilyy. Ohjelman poistomerkintä sisältyy varmuuskopioihin.") { vm.run { vm.repo.delete(p.id) } }
            } }
        }
    }
    if (create) EditDialog("Uusi ohjelma", listOf("Nimi"), listOf(""), { create = false }) { v -> require(v[0].isNotBlank()); val p = TrainingProgram(name = v[0]); vm.save(record(p.id, "program", p)) }
}
@Composable fun ExerciseLibrary(vm: SaliViewModel, rows: List<Record>) {
    var query by rememberSaveable { mutableStateOf("") }; var muscle by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<Exercise?>(null) }
    OutlinedTextField(query, { query = it }, label = { Text("Hae liikettä") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(muscle, { muscle = it }, label = { Text("Lihasryhmän suodatus") }, modifier = Modifier.fillMaxWidth())
    Action("Lisää oma liike tai laite") { edit = Exercise(name = "") }
    rows.values<Exercise>("exercise").filter { it.name.contains(query, true) && it.muscle.contains(muscle, true) }.sortedBy { it.name }.forEach { e -> CardBlock {
        Text(e.name, style = MaterialTheme.typography.titleMedium); Text("${e.muscle} · ${loadLabel(e.load)}")
        TextButton(onClick = { edit = e }) { Text("Muokkaa") }
        Confirm("Poista liike", "Poista liike ensin ohjelmien päivistä. Historialliset suoritukset ja kuvat säilyvät.") { vm.run { vm.repo.delete(e.id) } }
    } }
    edit?.let { e -> ExerciseDialog(e, { edit = null }) { updated -> vm.save(record(updated.id, "exercise", updated)); edit = null } }
}
fun loadLabel(load: Load) = when (load) { Load.EXTERNAL -> "Ulkoinen paino"; Load.BODY -> "Kehonpaino"; Load.ASSISTED -> "Lisäpaino / avustus" }
@Composable fun ExerciseDialog(exercise: Exercise, close: () -> Unit, save: (Exercise) -> Unit) {
    var e by remember { mutableStateOf(exercise) }; var inc by remember { mutableStateOf(kg(e.increment)) }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launchSafe({ error = it }) {
            val photo = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { input -> input.readBytesLimited(12 * 1024 * 1024) }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Kuvaa ei voitu lukea" }
                var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 800) sample *= 2
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
                val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out); bitmap.recycle()
                require(out.size() <= 300000) { "Valitse pienempi kuva" }; Base64.getEncoder().encodeToString(out.toByteArray())
            }
            e = e.copy(photo = photo)
        }
    }
    AlertDialog(onDismissRequest = close, title = { Text("Liikkeen tiedot") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(e.name, { e = e.copy(name = it) }, label = { Text("Nimi") })
            OutlinedTextField(e.muscle, { e = e.copy(muscle = it) }, label = { Text("Lihasryhmä") })
            OutlinedTextField(e.equipment, { e = e.copy(equipment = it) }, label = { Text("Väline / laite") })
            Load.entries.forEach { l -> FilterChip(selected = e.load == l, onClick = { e = e.copy(load = l) }, label = { Text(loadLabel(l)) }) }
            OutlinedTextField(inc, { inc = it }, label = { Text("Painonkorotus kg") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(e.notes, { e = e.copy(notes = it) }, label = { Text("Muistiinpanot") })
            Photo(e.photo)
            TextButton(onClick = { launcher.launch("image/*") }) { Text("Valitse kuva galleriasta") }
            if (e.photo != null) TextButton(onClick = { e = e.copy(photo = null) }) { Text("Poista kuva") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { try { val updated = e.copy(increment = grams(inc)); validateExercise(updated); save(updated) } catch (ex: Exception) { error = ex.message } }) { Text("Tallenna") } }, dismissButton = { TextButton(onClick = close) { Text("Peruuta") } })
}
@Composable fun Photo(photo: String?) {
    if (photo != null) {
        val bitmap = remember(photo) { runCatching { val b = Base64.getDecoder().decode(photo); BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull() }
        bitmap?.let { Image(it, "Liikkeen kuva", Modifier.fillMaxWidth().height(140.dp)) }
    }
}
@Composable fun PickExercise(rows: List<Record>, close: () -> Unit, pick: (Exercise) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Valitse liike") }, text = {
        Column { OutlinedTextField(query, { query = it }, label = { Text("Hae") }); Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            rows.values<Exercise>("exercise").filter { it.name.contains(query, true) }.sortedBy { it.name }.forEach { e -> TextButton(onClick = { pick(e); close() }, modifier = Modifier.fillMaxWidth()) { Text(e.name) } }
        } }
    }, confirmButton = { TextButton(onClick = close) { Text("Sulje") } })
}
@Composable fun ProgramEditor(vm: SaliViewModel, rows: List<Record>, id: String) {
    val p = rows.find { it.id == id && !it.deleted }?.value<TrainingProgram>() ?: return
    var rename by remember { mutableStateOf(false) }; var dayEdit by remember { mutableStateOf<ProgramDay?>(null) }
    var addTo by remember { mutableStateOf<String?>(null) }; var targetEdit by remember { mutableStateOf<Pair<String, ProgramExercise>?>(null) }
    Page(p.name) {
        TextButton(onClick = { rename = true }) { Text("Nimeä ohjelma") }
        Action("Lisää treenipäivä") { dayEdit = ProgramDay(name = "") }
        p.days.forEachIndexed { di, d -> CardBlock {
            Text(d.name, style = MaterialTheme.typography.titleLarge)
            Row { TextButton(onClick = { dayEdit = d }) { Text("Nimi") }; TextButton(onClick = { vm.program(id) { it.copy(days = it.days.move(di, -1)) } }) { Text("↑") }; TextButton(onClick = { vm.program(id) { it.copy(days = it.days.move(di, 1)) } }) { Text("↓") } }
            d.exercises.forEachIndexed { ei, target ->
                val e = rows.find { it.id == target.exerciseId }?.value<Exercise>()
                Text("${ei + 1}. ${e?.name ?: "Poistettu liike"} · ${target.sets} × ${target.low}–${target.high}")
                if (target.notes.isNotBlank()) Text(target.notes)
                Row {
                    TextButton(onClick = { targetEdit = d.id to target }) { Text("Tavoite") }
                    TextButton(onClick = { vm.program(id) { p0 -> p0.copy(days = p0.days.map { if (it.id == d.id) it.copy(exercises = it.exercises.move(ei, -1)) else it }) } }) { Text("↑") }
                    TextButton(onClick = { vm.program(id) { p0 -> p0.copy(days = p0.days.map { if (it.id == d.id) it.copy(exercises = it.exercises.move(ei, 1)) else it }) } }) { Text("↓") }
                    TextButton(onClick = { vm.program(id) { p0 -> p0.copy(days = p0.days.map { if (it.id == d.id) it.copy(exercises = it.exercises.filterNot { t -> t.id == target.id }) else it }) } }) { Text("Poista") }
                }
            }
            Action("Lisää liike") { addTo = d.id }
            Confirm("Poista päivä", "Päivä poistetaan ohjelmasta. Treenihistoria säilyy.") { vm.program(id) { it.copy(days = it.days.filterNot { day -> day.id == d.id }) } }
        } }
    }
    if (rename) EditDialog("Ohjelman nimi", listOf("Nimi"), listOf(p.name), { rename = false }) { v -> require(v[0].isNotBlank()); vm.program(id) { it.copy(name = v[0]) } }
    dayEdit?.let { d -> EditDialog("Treenipäivä", listOf("Nimi"), listOf(d.name), { dayEdit = null }) { v ->
        require(v[0].isNotBlank()); vm.program(id) { old -> old.copy(days = if (old.days.any { it.id == d.id }) old.days.map { if (it.id == d.id) it.copy(name = v[0]) else it } else old.days + d.copy(name = v[0])) }
    } }
    addTo?.let { dayId -> PickExercise(rows, { addTo = null }) { e -> vm.program(id) { old -> old.copy(days = old.days.map { if (it.id == dayId) it.copy(exercises = it.exercises + ProgramExercise(exerciseId = e.id)) else it }) } } }
    targetEdit?.let { (dayId, t) -> EditDialog("Sarjatavoite", listOf("Sarjat", "Toistot vähintään", "Toistot enintään", "Huomiot"), listOf("${t.sets}", "${t.low}", "${t.high}", t.notes), { targetEdit = null }) { v ->
        val updated = t.copy(sets = v[0].toInt(), low = v[1].toInt(), high = v[2].toInt(), notes = v[3]); require(updated.sets in 1..30 && updated.low in 1..1000 && updated.high in updated.low..1000)
        vm.program(id) { old -> old.copy(days = old.days.map { d -> if (d.id == dayId) d.copy(exercises = d.exercises.map { if (it.id == t.id) updated else it }) else d }) }
    } }
}
@Composable fun WorkoutEditor(vm: SaliViewModel, rows: List<Record>, settings: Settings, sessionId: String) {
    val s = rows.find { it.id == sessionId }?.value<WorkoutSession>() ?: return
    val context = LocalContext.current
    DisposableEffect(settings.keepScreen, s.status) {
        val window = (context as? Activity)?.window
        if (settings.keepScreen && s.status == Status.ACTIVE) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var add by remember { mutableStateOf(false) }; var replace by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(false) }; var notes by remember { mutableStateOf<WorkoutExercise?>(null) }
    Page(s.dayName) {
        Text("${s.programName} · ${date(s.started)}")
        Text(if (s.status == Status.ACTIVE) "Kesken · muutokset tallentuvat automaattisesti" else if (s.status == Status.CANCELLED) "Peruttu" else "Valmis · voit korjata tietoja")
        TextButton(onClick = { info = true }) { Text("Ajankohta ja muistiinpanot") }
        if (s.notes.isNotBlank()) Text(s.notes)
        if (s.status != Status.CANCELLED) {
            s.exercises.forEachIndexed { index, ex -> key(ex.id) { CardBlock {
                Text(ex.exercise.name, style = MaterialTheme.typography.titleLarge)
                Text("Tavoite ${ex.target.sets} × ${ex.target.low}–${ex.target.high} · ${loadLabel(ex.exercise.load)}")
                if (ex.target.notes.isNotBlank()) Text(ex.target.notes)
                if (ex.notes.isNotBlank()) Text(ex.notes)
                Photo(ex.exercise.photo)
                val previous = rows.values<WorkoutSession>("session").filter { it.id != s.id && it.status != Status.CANCELLED && it.started <= s.started }.sortedByDescending { it.started }.firstNotNullOfOrNull { session -> session.exercises.firstOrNull { it.exercise.id == ex.exercise.id && !it.skipped && it.sets.any { set -> set.done } } }
                if (previous != null) {
                    Text("Edellinen: " + previous.sets.filter { it.done }.joinToString(" · ") { setSummary(it, previous.exercise.load) })
                    suggestion(previous)?.let { Text("Ehdotus: $it", color = MaterialTheme.colorScheme.primary) }
                }
                Row {
                    TextButton(onClick = { vm.session(s.id) { it.copy(exercises = it.exercises.move(index, -1)) } }) { Text("↑") }
                    TextButton(onClick = { vm.session(s.id) { it.copy(exercises = it.exercises.move(index, 1)) } }) { Text("↓") }
                    TextButton(onClick = { notes = ex }) { Text("Huomiot") }
                    TextButton(onClick = { replace = ex.id }) { Text("Vaihda") }
                }
                Row { Checkbox(checked = ex.skipped, onCheckedChange = { value -> vm.exercise(s.id, ex.id) { it.copy(skipped = value) } }); Text("Ohita liike", modifier = Modifier.padding(top = 12.dp)) }
                if (!ex.skipped) {
                    ex.sets.forEachIndexed { si, set -> key(set.id) { SetEditor(set, ex.exercise.load, si + 1, { updated -> vm.exercise(s.id, ex.id) { e -> e.copy(sets = e.sets.map { if (it.id == set.id) updated else it }) } }, { vm.exercise(s.id, ex.id) { e -> e.copy(sets = e.sets.filterNot { it.id == set.id }) } }) } }
                    TextButton(onClick = { vm.exercise(s.id, ex.id) { it.copy(sets = it.sets + WorkoutSet(body = it.sets.lastOrNull()?.body)) } }) { Text("+ Lisää sarja") }
                    suggestion(ex)?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
                Confirm("Poista liike treenistä", "Tämän treenin liike ja sen sarjat poistetaan. Ohjelma ei muutu.") { vm.session(s.id) { it.copy(exercises = it.exercises.filterNot { e -> e.id == ex.id }) } }
            } } }
            Action("Lisää liike treeniin") { add = true }
            if (s.status == Status.ACTIVE) {
                Action("Lopeta treeni") { vm.session(s.id) { it.copy(status = Status.FINISHED, ended = maxOf(System.currentTimeMillis(), it.started)) } }
                Text("Voit jättää treenin kesken siirtymällä toiseen näkymään.")
                Confirm("Peru aloitus", "Treeni merkitään perutuksi ja sen vaikutus kiertoon poistuu. Myöhemmät aloitukset säilyvät.") { vm.session(s.id) { it.copy(status = Status.CANCELLED, ended = null) } }
            } else Action("Avaa uudelleen keskeneräiseksi") { vm.session(s.id) { it.copy(status = Status.ACTIVE, ended = null) } }
        }
    }
    if (add || replace != null) PickExercise(rows, { add = false; replace = null }) { e ->
        val replaceId = replace
        vm.session(s.id) { old ->
            val target = ProgramExercise(exerciseId = e.id)
            val new = WorkoutExercise(exercise = e, target = target, sets = List(target.sets) { WorkoutSet() })
            old.copy(exercises = if (replaceId == null) old.exercises + new else old.exercises.map { if (it.id == replaceId) new else it })
        }
    }
    if (info) EditDialog("Treenin tiedot", listOf("Alku (pp.kk.vvvv tt:mm)", "Loppu (tyhjä = kesken)", "Muistiinpanot"), listOf(date(s.started), s.ended?.let(::date) ?: "", s.notes), { info = false }) { v ->
        val start = timestamp(v[0]); val end = v[1].takeIf { it.isNotBlank() }?.let(::timestamp); require(end == null || end >= start) { "Loppu on ennen alkua" }
        vm.session(s.id) { it.copy(started = start, ended = end, notes = v[2], status = if (it.status == Status.CANCELLED) Status.CANCELLED else if (end == null) Status.ACTIVE else Status.FINISHED) }
    }
    notes?.let { e -> EditDialog("Liikkeen huomiot", listOf("Huomiot"), listOf(e.notes), { notes = null }) { v -> vm.exercise(s.id, e.id) { it.copy(notes = v[0]) } } }
}
fun setSummary(s: WorkoutSet, load: Load) = when (load) {
    Load.EXTERNAL -> "${kg(s.grams)} kg × ${s.reps}"
    Load.BODY -> "${s.reps} toistoa"
    Load.ASSISTED -> "+${kg(s.added)} / −${kg(s.assistance)} kg × ${s.reps}"
}
@Composable fun SetEditor(set: WorkoutSet, load: Load, number: Int, save: (WorkoutSet) -> Unit, delete: () -> Unit) {
    var weight by rememberSaveable(set.id) { mutableStateOf(kg(set.grams)) }; var reps by rememberSaveable(set.id) { mutableStateOf("${set.reps}") }
    var body by rememberSaveable(set.id) { mutableStateOf(set.body?.let(::kg) ?: "") }; var added by rememberSaveable(set.id) { mutableStateOf(kg(set.added)) }; var assistance by rememberSaveable(set.id) { mutableStateOf(kg(set.assistance)) }
    var invalid by remember { mutableStateOf(false) }
    fun persist(done: Boolean = set.done) {
        try {
            val r = reps.toInt(); require(r in 0..1000 && (!done || r > 0))
            val update = set.copy(grams = grams(weight), reps = r, body = body.takeIf { it.isNotBlank() }?.let(::grams), added = grams(added), assistance = grams(assistance), done = done)
            require(update.body == null || update.body > 0); require(update.added == 0L || update.assistance == 0L)
            invalid = false; save(update)
        } catch (_: Exception) { invalid = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sarja $number", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (load == Load.EXTERNAL) OutlinedTextField(weight, { weight = it; persist() }, label = { Text("kg") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(reps, { reps = it; persist() }, label = { Text("Toistot") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
        if (load != Load.EXTERNAL) OutlinedTextField(body, { body = it; persist() }, label = { Text("Kehonpaino kg (valinnainen)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        if (load == Load.ASSISTED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(added, { added = it; persist() }, label = { Text("Lisäpaino kg") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(assistance, { assistance = it; persist() }, label = { Text("Avustus kg") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        }
        Row { Checkbox(set.done, { persist(it) }); Text("Tehty", Modifier.padding(top = 12.dp)); Spacer(Modifier.weight(1f)); TextButton(onClick = delete) { Text("Poista sarja") } }
        if (invalid) Text("Tarkista luvut. Virheellistä arvoa ei tallennettu. Käytä joko lisäpainoa tai avustusta.", color = MaterialTheme.colorScheme.error)
    }
}
