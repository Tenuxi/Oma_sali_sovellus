package fi.omasali.app

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZonedDateTime
import kotlin.math.hypot

enum class Metric(val key: String, val title: String, val unit: String) {
    WEIGHT("weight", "Suurin ulkoinen paino", "kg"), REPS("reps", "Toistot", "toistoa"),
    FIXED("fixed", "Toistot tietyllä painolla", "toistoa"), VOLUME("volume", "Treenivolyymi", "kg"),
    RM("rm", "Arvioitu 1RM", "kg, arvio"), ADDED("added", "Suurin lisäpaino", "kg"), ASSISTANCE("assistance", "Pienin avustus", "kg")
}
fun availableMetrics(loads: Set<Load>): List<Metric> = Metric.entries.filter { metric -> when (metric) {
    Metric.REPS -> true
    Metric.ADDED, Metric.ASSISTANCE -> Load.ASSISTED in loads
    else -> Load.EXTERNAL in loads
} }
fun metricPoints(sessions: List<WorkoutSession>, exerciseId: String, metric: Metric, fixedWeight: Long? = null): List<Pair<Long, Double>> = sessions.filter { it.status != Status.CANCELLED }.sortedBy { it.started }.mapNotNull { session ->
    val exercises = session.exercises.filter { it.exercise.id == exerciseId && !it.skipped }
    val external = exercises.filter { it.exercise.load == Load.EXTERNAL }
    val sets = exercises.flatMap { it.sets }.filter { it.done }
    val externalSets = external.flatMap { it.sets }.filter { it.done }
    val assistedSets = exercises.filter { it.exercise.load == Load.ASSISTED }.flatMap { it.sets }.filter { it.done }
    val value = when (metric) {
        Metric.WEIGHT -> externalSets.maxOfOrNull { it.grams / 1000.0 }
        Metric.REPS -> sets.maxOfOrNull { it.reps.toDouble() }
        Metric.FIXED -> externalSets.filter { it.grams == fixedWeight }.maxOfOrNull { it.reps.toDouble() }
        Metric.VOLUME -> if (externalSets.isEmpty()) null else external.sumOf(::volume) / 1000.0
        Metric.RM -> external.mapNotNull(::oneRm).maxOrNull()
        Metric.ADDED -> assistedSets.maxOfOrNull { it.added / 1000.0 }
        Metric.ASSISTANCE -> assistedSets.minOfOrNull { it.assistance / 1000.0 }
    }
    value?.let { session.started to it }
}
private fun number(value: Double) = java.math.BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
@Composable fun Chart(title: String, points: List<Pair<Long, Double>>, unit: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (points.isEmpty()) { Text("Ei kirjattuja suorituksia valitulla aikavälillä."); return }
    val sorted = points.sortedBy { it.first }
    val min = sorted.minOf { it.second }; val max = sorted.maxOf { it.second }
    val range = (max - min).coerceAtLeast(1.0)
    val span = (sorted.last().first - sorted.first().first).coerceAtLeast(1).toDouble()
    var selected by remember(sorted) { mutableIntStateOf(sorted.lastIndex) }
    val color = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Text("${number(sorted.last().second)} $unit", style = MaterialTheme.typography.headlineMedium)
    Text("Viimeisin · ${date(sorted.last().first)}", style = MaterialTheme.typography.bodySmall)
    fun position(index: Int, width: Float, height: Float, left: Float, padding: Float): Offset {
        val (time, value) = sorted[index]
        val fraction = if (sorted.size == 1 || sorted.first().first == sorted.last().first) .5 else (time - sorted.first().first) / span
        return Offset(left + fraction.toFloat() * (width - left - padding).coerceAtLeast(1f), height - padding - ((value - min) / range).toFloat() * (height - 2 * padding))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val left = with(density) { 44.dp.toPx() }; val padding = with(density) { 14.dp.toPx() }
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "$title, ${sorted.size} pistettä. Valittu ${date(sorted[selected].first)}, ${number(sorted[selected].second)} $unit" }.pointerInput(sorted) {
        detectTapGestures { touch -> selected = sorted.indices.minByOrNull { i -> val p = position(i, size.width.toFloat(), size.height.toFloat(), left, padding); hypot(p.x - touch.x, p.y - touch.y) } ?: 0 }
    }) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = labelColor.toArgb(); textSize = 11.sp.toPx() }
        repeat(3) { tick ->
            val y = size.height - padding - tick / 2f * (size.height - 2 * padding)
            drawLine(gridColor, Offset(left, y), Offset(size.width - padding, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(number(min + range * tick / 2), 0f, y + 4.dp.toPx(), paint)
        }
        val path = Path()
        sorted.indices.forEach { i -> val p = position(i, size.width, size.height, left, padding); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        sorted.indices.forEach { i -> drawCircle(color, (if (i == selected) 6 else 3).dp.toPx(), position(i, size.width, size.height, left, padding)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(date(sorted.first().first).substringBefore(' '), style = MaterialTheme.typography.labelSmall); Text(date(sorted.last().first).substringBefore(' '), style = MaterialTheme.typography.labelSmall) }
    Text("${date(sorted[selected].first)} · ${number(sorted[selected].second)} $unit", color = MaterialTheme.colorScheme.primary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { selected-- }, enabled = selected > 0) { Text("← Edellinen piste") }
        TextButton(onClick = { selected++ }, enabled = selected < sorted.lastIndex) { Text("Seuraava piste →") }
    }
}
@Composable fun Statistics(vm: SaliViewModel, rows: List<Record>) {
    val selection = vm.ui.stats
    var weightEdit by remember { mutableStateOf<BodyWeightEntry?>(null) }
    var deleting by remember { mutableStateOf<BodyWeightEntry?>(null) }
    val months = selection.months.takeIf { it in listOf(0, 1, 3) } ?: 0
    val after = if (months == 0) 0 else ZonedDateTime.now().minusMonths(months.toLong()).toInstant().toEpochMilli()
    val allSessions = rows.values<WorkoutSession>("session")
    val sessions = allSessions.filter { it.started >= after }
    val history = allSessions.flatMap { it.exercises }.map { it.exercise }
    val deletedIds = rows.filter { it.kind == "exercise" && it.deleted }.map { it.id }.toSet()
    val exercises = (rows.values<Exercise>("exercise") + history).distinctBy { it.id }.sortedBy { it.name }
    // Keep deleted exercise history available, but don't restore a deleted selection automatically.
    val fallback = exercises.firstOrNull { it.id !in deletedIds } ?: exercises.firstOrNull()
    val exercise = exercises.find { it.id == selection.exerciseId } ?: fallback
    val loads = exercise?.let { e -> (history.filter { it.id == e.id }.map { it.load } + e.load).toSet() } ?: setOf(Load.EXTERNAL)
    val metrics = availableMetrics(loads)
    val metric = metrics.find { it.key == selection.metric } ?: metrics.first()
    LaunchedEffect(Unit) {
        if (exercises.isNotEmpty() && (selection.exerciseId in deletedIds || exercises.none { it.id == selection.exerciseId })) vm.stats { it.copy(exerciseId = fallback?.id) }
    }
    LaunchedEffect(exercise?.id, metric.key) { if (selection.metric != metric.key) vm.stats { it.copy(metric = metric.key) } }
    Page("Kehitys") {
        ChipStrip {
            FilterChip(selection.section != "body", { vm.stats { it.copy(section = "exercises") } }, label = { Text("Liikkeet") })
            FilterChip(selection.section == "body", { vm.stats { it.copy(section = "body") } }, label = { Text("Kehonpaino") })
        }
        if (selection.section != "body") {
            ChoiceField("Liike", exercise?.name ?: "Ei liikkeitä", exercises.map { it.id to (it.name + if (it.id in deletedIds) " (poistettu)" else "") }) { id -> vm.stats { it.copy(exerciseId = id) } }
            ChoiceField("Mittari", metric.title, metrics.map { it.key to it.title }) { key -> vm.stats { it.copy(metric = key) } }
        }
        ChipStrip { listOf(1 to "1 kk", 3 to "3 kk", 0 to "Kaikki").forEach { (n, text) -> FilterChip(months == n, { vm.stats { it.copy(months = n) } }, label = { Text(text) }) } }
        if (selection.section == "body") {
            val weights = rows.values<BodyWeightEntry>("weight").filter { it.time >= after }.sortedBy { it.time }
            CardBlock { Chart("Kehonpaino", weights.map { it.time to it.grams / 1000.0 }, "kg") }
            Action("＋ Lisää punnitus") { weightEdit = BodyWeightEntry(grams = 70000, time = System.currentTimeMillis()) }
            Text("Punnitushistoria", style = MaterialTheme.typography.titleMedium)
            weights.reversed().forEach { w -> CompactRow("${kg(w.grams)} kg", date(w.time), "↗", { weightEdit = w }) {
                MoreMenu("Punnituksen toiminnot", listOf(MenuAction("Muokkaa") { weightEdit = w }, MenuAction("Poista") { deleting = w }))
            } }
        } else {
            if (metric == Metric.FIXED) OutlinedTextField(selection.fixedWeight, { v -> vm.stats { it.copy(fixedWeight = v) } }, label = { Text("Ulkoinen paino kg") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val fixed = runCatching { grams(selection.fixedWeight) }.getOrNull()
            val points = exercise?.let { metricPoints(sessions, it.id, metric, fixed) } ?: emptyList()
            CardBlock { Chart(metric.title, points, metric.unit) }
            Text(if (metric == Metric.RM) "Epley-arvio ulkoisesta painosta, vain 1–12 toiston sarjat. Ei mitattu maksimi." else "Vain tehdyt sarjat, myös keskeneräisistä treeneistä.", style = MaterialTheme.typography.bodySmall)
            if (metric == Metric.WEIGHT && points.isNotEmpty()) Text("Painoennätys valitulla aikavälillä: ${number(points.maxOf { it.second })} kg", style = MaterialTheme.typography.bodySmall)
        }
    }
    deleting?.let { w -> ConfirmDialog("Poista punnitus?", "Historiallisten sarjojen kehonpaino ei muutu.", { deleting = null }) { vm.run { vm.repo.delete(w.id) } } }
    weightEdit?.let { w -> EditDialog("Punnitus", listOf("Paino kg", "Ajankohta (pp.kk.vvvv tt:mm)"), listOf(kg(w.grams), date(w.time)), { weightEdit = null }) { v ->
        val updated = w.copy(grams = grams(v[0]), time = timestamp(v[1])); require(updated.grams > 0 && updated.time > 0); vm.save(record(w.id, "weight", updated))
    } }
}
