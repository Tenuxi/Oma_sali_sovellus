package fi.omasali.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

val Context.preferences by preferencesDataStore("device_preferences")
class Repository(val db: SaliDatabase) {
    val records = db.records().observe().map(::decodeRows)
    suspend fun all() = decodeRows(db.records().all())
    private suspend fun write(records: List<Record>) {
        records.forEach { r -> db.records().removeParts(r.id); db.records().put(encodeRows(r)) }
    }
    suspend fun initialize() = db.withTransaction {
        if (all().isEmpty()) {
            val names = listOf("Penkkipunnerrus" to "Rinta", "Kyykky" to "Jalat", "Maastaveto" to "Selkä", "Pystypunnerrus" to "Olkapäät", "Kulmasoutu" to "Selkä", "Jalkaprässi" to "Jalat", "Hauiskääntö" to "Kädet", "Ojentajapunnerrus" to "Kädet", "Punnerrus" to "Rinta", "Leuanveto" to "Selkä")
            write(names.mapIndexed { i, (n, m) ->
                val e = Exercise(id = "builtin-$i", name = n, muscle = m, equipment = if (i == 5) "Laite" else "", load = if (i == 8) Load.BODY else if (i == 9) Load.ASSISTED else Load.EXTERNAL, increment = if (i == 5) 5000 else 2500)
                record(e.id, "exercise", e)
            } + record("settings", "settings", Settings()))
        }
    }
    // The transform reads fresh data inside the same transaction, avoiding lost set edits.
    suspend fun mutate(id: String, transform: (Record?) -> Record) = db.withTransaction {
        val current = all(); val old = current.find { it.id == id }
        val candidate = transform(old)
        require(candidate.id == id && (old == null || old.kind == candidate.kind))
        val updated = candidate.copy(revision = id(), ancestors = old?.let { it.ancestors + it.revision } ?: emptySet())
        val result = current.filterNot { it.id == id } + updated
        validateRecords(result); write(listOf(updated))
    }
    suspend fun save(record: Record) = mutate(record.id) { record }
    suspend fun delete(id: String) = mutate(id) { requireNotNull(it).copy(deleted = true) }
    suspend fun updateProgram(id: String, change: (TrainingProgram) -> TrainingProgram) = mutate(id) { r -> record(id, "program", change(requireNotNull(r).value())) }
    suspend fun updateSession(id: String, change: (WorkoutSession) -> WorkoutSession) = mutate(id) { r -> record(id, "session", change(requireNotNull(r).value())) }
    suspend fun settings(change: (Settings) -> Settings) = mutate("settings") { r -> record("settings", "settings", change(r?.value() ?: Settings())) }
    suspend fun start(programId: String, dayId: String): String = db.withTransaction {
        val rows = all(); val p = rows.first { it.id == programId && !it.deleted }.value<TrainingProgram>()
        val day = p.days.first { it.id == dayId }; require(day.exercises.isNotEmpty()) { "Lisää päivään vähintään yksi liike" }
        val now = System.currentTimeMillis()
        val body = rows.filter { it.kind == "weight" && !it.deleted }.map { it.value<BodyWeightEntry>() }.filter { it.time <= now }.maxByOrNull { it.time }?.grams
        val order = (rows.filter { it.kind == "session" }.maxOfOrNull { it.value<WorkoutSession>().cycleOrder } ?: 0) + 1
        val s = WorkoutSession(programId = p.id, programName = p.name, dayId = day.id, dayName = day.name, started = now, cycleOrder = order, cycleDays = p.days.map { it.id }, exercises = day.exercises.map { t ->
            val e = rows.first { it.id == t.exerciseId && !it.deleted }.value<Exercise>()
            WorkoutExercise(exercise = e, target = t, sets = List(t.sets) { WorkoutSet(body = if (e.load == Load.EXTERNAL) null else body) })
        })
        val r = record(s.id, "session", s); validateRecords(rows + r); write(listOf(r)); s.id
    }
    suspend fun export(): String = db.withTransaction { codec.encodeToString(Backup(records = all())) }
    suspend fun merge(expected: List<Record>, backup: Backup, choices: Map<String, Boolean>) = db.withTransaction {
        val current = all()
        require(current == expected) { "Tiedot muuttuivat esikatselun jälkeen. Avaa esikatselu uudelleen." }
        val changes = preview(current, backup.records).mapNotNull { item ->
            when (item.type) {
                MergeType.ADD, MergeType.UPDATE -> item.incoming
                MergeType.CONFLICT -> resolve(item, choices[item.incoming.id] ?: error("Valitse jokaisen ristiriidan ratkaisu"))
                else -> null
            }
        }
        val ids = changes.map { it.id }.toSet()
        validateRecords(current.filterNot { it.id in ids } + changes)
        // Images are bounded JPEG blobs inside each payload: one SQLite transaction covers images too.
        write(changes)
    }
    suspend fun template(parts: Int) {
        val exercises = all().filter { it.kind == "exercise" && !it.deleted }.map { it.value<Exercise>() }
        val names = if (parts == 2) listOf("Ylävartalo", "Alavartalo") else listOf("Työntävät", "Vetävät", "Jalat")
        val p = TrainingProgram(name = "$parts-jakoinen", days = names.mapIndexed { index, n ->
            ProgramDay(name = n, exercises = exercises.filter {
                if (parts == 2) (it.muscle == "Jalat") == (index == 1)
                else when (index) { 0 -> it.muscle in listOf("Rinta", "Olkapäät"); 1 -> it.muscle in listOf("Selkä", "Kädet"); else -> it.muscle == "Jalat" }
            }.map { ProgramExercise(exerciseId = it.id) })
        })
        save(record(p.id, "program", p)); settings { it.copy(activeProgram = p.id) }
    }
}
