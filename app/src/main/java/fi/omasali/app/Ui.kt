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
        NavigationBar { tabs.forEachIndexed { i, (key, title) -> NavigationBarItem(selected = route == key || (key == "programs" && route.startsWith("program/")) || (key == "workouts" && route.startsWith("session/")), onClick = { nav.navigate(key) { popUpTo("home"); launchSingleTop = true } }, icon = { Text(listOf("⌂", "●", "≡", "↗", "⚙")[i]) }, label = { Text(title, maxLines = 1) }) } }
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
@Composable fun Page(title: String, scrollKey: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    val pageScroll = rememberScrollState()
    LaunchedEffect(scrollKey) { if (scrollKey != null) pageScroll.scrollTo(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().verticalScroll(pageScroll).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
        Spacer(Modifier.height(24.dp))
    }
    }
}
@Composable fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content) }
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
@Composable fun Workouts(rows: List<Record>, open: (String) -> Unit) {
    Page("Treenit") {
        val sessions = rows.values<WorkoutSession>("session").filter { it.status != Status.CANCELLED }.sortedByDescending { it.started }
        if (sessions.isEmpty()) Text("Aloita ensimmäinen treeni etusivulta.")
        sessions.forEach { s -> CardBlock {
            Text("${s.programName} · ${s.dayName}", style = MaterialTheme.typography.titleMedium)
            Text("${date(s.started)} · ${if (s.status == Status.ACTIVE) "Kesken" else "Valmis"}")
            Text("${summarize(s).completedSets} sarjaa · ${kg(s.exercises.sumOf(::volume))} kg volyymi")
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
            val orderedPrograms = rows.values<TrainingProgram>("program").sortedBy { p -> settings.programOrder.indexOf(p.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            orderedPrograms.forEachIndexed { pi, p -> CardBlock {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text("${p.days.size} treenipäivää")
                Row {
                    TextButton(onClick = { vm.run { vm.repo.settings { it.copy(programOrder = orderedPrograms.map { program -> program.id }.move(pi, -1)) } } }) { Text("Siirrä ylös") }
                    TextButton(onClick = { vm.run { vm.repo.settings { it.copy(programOrder = orderedPrograms.map { program -> program.id }.move(pi, 1)) } } }) { Text("Siirrä alas") }
                }
                Action("Muokkaa") { open(p.id) }
                TextButton(onClick = { vm.run { vm.repo.settings { it.copy(activeProgram = p.id) } } }) { Text(if (settings.activeProgram == p.id) "✓ Aktiivinen ohjelma" else "Aseta aktiiviseksi") }
                Confirm("Poista ohjelma", "Treenihistoria säilyy. Ohjelman poistomerkintä sisältyy varmuuskopioihin.") { vm.run { vm.repo.delete(p.id) } }
            } }
        }
    }
    if (create) EditDialog("Uusi ohjelma", listOf("Nimi"), listOf(""), { create = false }) { v -> require(v[0].isNotBlank()); val p = TrainingProgram(name = v[0]); vm.save(record(p.id, "program", p)) }
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
fun setSummary(s: WorkoutSet, load: Load) = when (load) {
    Load.EXTERNAL -> "${kg(s.grams)} kg × ${s.reps}"
    Load.BODY -> "${s.reps} toistoa"
    Load.ASSISTED -> "+${kg(s.added)} / −${kg(s.assistance)} kg × ${s.reps}"
}
