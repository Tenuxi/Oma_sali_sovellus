package fi.omasali.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime

@Composable fun Chart(title: String, points: List<Pair<Long, Double>>, unit: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (points.isEmpty()) { Text("Ei kirjattuja suorituksia valitulla aikavälillä."); return }
    val sorted = points.sortedBy { it.first }; val min = sorted.minOf { it.second }; val max = sorted.maxOf { it.second }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val span = (sorted.last().first - sorted.first().first).coerceAtLeast(1).toDouble()
        val range = (max - min).coerceAtLeast(1.0)
        val path = Path()
        sorted.forEachIndexed { index, (time, value) ->
            val x = 8f + ((time - sorted.first().first) / span).toFloat() * (size.width - 16f)
            val y = size.height - 8f - ((value - min) / range).toFloat() * (size.height - 16f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 4f, Offset(x, y))
        }
        drawPath(path, color, style = Stroke(3f))
    }
    Text("${"%.1f".format(min)}–${"%.1f".format(max)} $unit · ${date(sorted.first().first).substringBefore(' ')}–${date(sorted.last().first).substringBefore(' ')}")
    Text("Viimeisin: ${"%.1f".format(sorted.last().second)} $unit")
}
@Composable fun Statistics(vm: SaliViewModel, rows: List<Record>) {
    var months by rememberSaveable { mutableStateOf(0) }; var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(false) }; var weightEdit by remember { mutableStateOf<BodyWeightEntry?>(null) }
    var fixedWeight by rememberSaveable { mutableStateOf("") }
    val after = if (months == 0) 0 else ZonedDateTime.now().minusMonths(months.toLong()).toInstant().toEpochMilli()
    val sessions = rows.values<WorkoutSession>("session").filter { it.status != Status.CANCELLED && it.started >= after }.sortedBy { it.started }
    val historyExercises = rows.values<WorkoutSession>("session").flatMap { it.exercises }.map { it.exercise }
    val exercises = (rows.values<Exercise>("exercise") + historyExercises).distinctBy { it.id }.sortedBy { it.name }
    val exercise = exercises.find { it.id == selected } ?: exercises.firstOrNull()
    Page("Kehitys") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1 to "1 kk", 3 to "3 kk", 0 to "Kaikki").forEach { (n, text) -> FilterChip(months == n, { months = n }, label = { Text(text) }) } }
        Text("Vain tehdyiksi merkityt sarjat lasketaan, myös keskeneräisistä treeneistä.")
        Action(exercise?.name ?: "Valitse liike") { pick = true }
        exercise?.let { e ->
            // Load type is read from the historical snapshot, not today's library settings.
            val data = sessions.flatMap { s -> s.exercises.filter { it.exercise.id == e.id && !it.skipped }.map { s.started to it } }
            val external = data.filter { it.second.exercise.load == Load.EXTERNAL }
            val body = data.filter { it.second.exercise.load != Load.EXTERNAL }
            CardBlock {
                Chart("Suurin ulkoinen paino", external.mapNotNull { (t, x) -> x.sets.filter { it.done }.maxOfOrNull { it.grams / 1000.0 }?.let { t to it } }, "kg")
                Chart("Toistoennätys / treeni", data.mapNotNull { (t, x) -> x.sets.filter { it.done }.maxOfOrNull { it.reps.toDouble() }?.let { t to it } }, "toistoa")
                OutlinedTextField(fixedWeight, { fixedWeight = it }, label = { Text("Seuraa tiettyä ulkoista painoa (kg)") }, modifier = Modifier.fillMaxWidth())
                val g = runCatching { grams(fixedWeight) }.getOrNull()
                if (g != null) Chart("Toistot painolla ${kg(g)} kg", external.mapNotNull { (t, x) -> x.sets.filter { it.done && it.grams == g }.maxOfOrNull { it.reps.toDouble() }?.let { t to it } }, "toistoa")
                Chart("Ulkoinen volyymi / treeni", external.groupBy { it.first }.map { (t, xs) -> t to xs.sumOf { volume(it.second) } / 1000.0 }, "kg")
                Chart("Arvioitu 1RM (Epley, 1–12 toistoa)", external.mapNotNull { (t, x) -> oneRm(x)?.let { t to it } }, "kg, arvio")
                if (body.isNotEmpty()) {
                    Chart("Suurin lisäpaino", body.mapNotNull { (t, x) -> x.sets.filter { it.done }.maxOfOrNull { it.added / 1000.0 }?.let { t to it } }, "kg")
                    Chart("Pienin avustus", body.mapNotNull { (t, x) -> x.sets.filter { it.done }.minOfOrNull { it.assistance / 1000.0 }?.let { t to it } }, "kg")
                    Text("Kehonpainoliikkeille ei lasketa 1RM-arviota tai ulkoisen painon volyymia.")
                }
                val maxWeight = external.flatMap { it.second.sets }.filter { it.done }.maxOfOrNull { it.grams }
                if (maxWeight != null) Text("Painoennätys valitulla aikavälillä: ${kg(maxWeight)} kg")
            }
        }
        Text("Kehonpaino", style = MaterialTheme.typography.titleLarge)
        Action("Lisää punnitus") { weightEdit = BodyWeightEntry(grams = 70000, time = System.currentTimeMillis()) }
        val weights = rows.values<BodyWeightEntry>("weight").filter { it.time >= after }.sortedBy { it.time }
        CardBlock { Chart("Kehonpainon kehitys", weights.map { it.time to it.grams / 1000.0 }, "kg") }
        weights.reversed().forEach { w -> CardBlock {
            Text("${date(w.time)} · ${kg(w.grams)} kg")
            TextButton(onClick = { weightEdit = w }) { Text("Korjaa") }
            Confirm("Poista punnitus", "Historiallisten sarjojen kehonpaino ei muutu.") { vm.run { vm.repo.delete(w.id) } }
        } }
    }
    if (pick) {
        val selectable = exercises.map { record(it.id, "exercise", it) }
        PickExercise(selectable, { pick = false }) { selected = it.id }
    }
    weightEdit?.let { w -> EditDialog("Punnitus", listOf("Paino kg", "Ajankohta (pp.kk.vvvv tt:mm)"), listOf(kg(w.grams), date(w.time)), { weightEdit = null }) { v ->
        val updated = w.copy(grams = grams(v[0]), time = timestamp(v[1])); require(updated.grams > 0 && updated.time > 0); vm.save(record(w.id, "weight", updated))
    } }
}
