package fi.omasali.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.UUID

fun id() = UUID.randomUUID().toString()
val codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }
@Serializable enum class Load { EXTERNAL, BODY, ASSISTED }
@Serializable data class Exercise(val id: String = id(), val name: String, val muscle: String = "Muu", val equipment: String = "", val load: Load = Load.EXTERNAL, val increment: Long = 2500, val photo: String? = null, val notes: String = "")
@Serializable data class ProgramExercise(val id: String = id(), val exerciseId: String, val sets: Int = 3, val low: Int = 8, val high: Int = 12, val notes: String = "")
@Serializable data class ProgramDay(val id: String = id(), val name: String, val exercises: List<ProgramExercise> = emptyList())
@Serializable data class TrainingProgram(val id: String = id(), val name: String, val days: List<ProgramDay> = emptyList())
@Serializable data class WorkoutSet(val id: String = id(), val grams: Long = 0, val reps: Int = 0, val body: Long? = null, val added: Long = 0, val assistance: Long = 0, val done: Boolean = false)
@Serializable data class WorkoutExercise(val id: String = id(), val exercise: Exercise, val target: ProgramExercise, val sets: List<WorkoutSet>, val skipped: Boolean = false, val notes: String = "")
@Serializable enum class Status { ACTIVE, FINISHED, CANCELLED }
@Serializable data class WorkoutSession(val id: String = id(), val programId: String, val programName: String, val dayId: String, val dayName: String, val started: Long, val ended: Long? = null, val status: Status = Status.ACTIVE, val exercises: List<WorkoutExercise>, val notes: String = "", val cycleOrder: Long, val cycleDays: List<String>)
@Serializable data class BodyWeightEntry(val id: String = id(), val grams: Long, val time: Long)
@Serializable data class Settings(val dark: Boolean = true, val keepScreen: Boolean = true, val activeProgram: String? = null, val programOrder: List<String> = emptyList())
@Serializable data class BackupMetadata(val format: Int = 1, val created: Long = System.currentTimeMillis(), val applicationId: String = "fi.omasali.app")
// Revision ancestry makes imports independent of wall-clock accuracy. Tombstones are never dropped.
@Serializable data class Record(val id: String, val kind: String, val payload: String, val revision: String = id(), val ancestors: Set<String> = emptySet(), val deleted: Boolean = false)
@Serializable data class Backup(val metadata: BackupMetadata = BackupMetadata(), val records: List<Record>)
inline fun <reified T> Record.value(): T = codec.decodeFromString(payload)
inline fun <reified T> record(id: String, kind: String, value: T) = Record(id, kind, codec.encodeToString(value))
fun kg(g: Long) = BigDecimal.valueOf(g, 3).stripTrailingZeros().toPlainString()
fun grams(s: String): Long = s.trim().replace(',', '.').toBigDecimal().movePointRight(3).longValueExact().also { require(it in 0..2_000_000) { "Painon on oltava 0–2000 kg, enintään kolme desimaalia." } }
fun nextDay(program: TrainingProgram, sessions: List<WorkoutSession>): ProgramDay? {
    if (program.days.isEmpty()) return null
    val latest = sessions.filter { it.programId == program.id && it.status != Status.CANCELLED }.maxWithOrNull(compareBy<WorkoutSession> { it.cycleOrder }.thenBy { it.id }) ?: return program.days.first()
    val index = program.days.indexOfFirst { it.id == latest.dayId }
    return program.days[if (index < 0) 0 else (index + 1) % program.days.size]
}
fun volume(ex: WorkoutExercise): Long = if (ex.exercise.load != Load.EXTERNAL || ex.skipped) 0 else ex.sets.filter { it.done }.sumOf { it.grams * it.reps }
fun oneRm(ex: WorkoutExercise): Double? = if (ex.exercise.load != Load.EXTERNAL || ex.skipped) null else ex.sets.filter { it.done && it.reps in 1..12 && it.grams > 0 }.maxOfOrNull { it.grams / 1000.0 * (1 + it.reps / 30.0) }
fun suggestion(ex: WorkoutExercise): String? {
    val sets = ex.sets.take(ex.target.sets)
    if (ex.skipped || sets.size != ex.target.sets || sets.any { !it.done || it.reps < ex.target.high || it.reps > 1000 }) return null
    val inc = ex.exercise.increment
    if (inc <= 0) return null
    return when (ex.exercise.load) {
        Load.EXTERNAL -> if (sets.all { it.grams == sets.first().grams && it.grams > 0 }) "Seuraavaksi ${kg(sets.first().grams + inc)} kg" else null
        Load.BODY -> null
        Load.ASSISTED -> if (sets.all { it.added == sets.first().added && it.assistance == sets.first().assistance } && sets.none { it.added > 0 && it.assistance > 0 }) {
            val s = sets.first()
            if (s.assistance > 0) "Seuraavaksi avustus ${kg((s.assistance - inc).coerceAtLeast(0))} kg" else "Seuraavaksi lisäpaino ${kg(s.added + inc)} kg"
        } else null
    }
}
enum class MergeType { ADD, UPDATE, SAME, KEEP, CONFLICT }
data class MergeItem(val incoming: Record, val current: Record?, val type: MergeType)
fun preview(current: List<Record>, incoming: List<Record>): List<MergeItem> {
    val map = current.associateBy { it.id }
    return incoming.map { r ->
        val c = map[r.id]
        require(c == null || c.kind == r.kind) { "Tunnisteen tyyppi muuttui" }
        val type = when {
            c == null -> MergeType.ADD
            c == r -> MergeType.SAME
            c.revision == r.revision -> error("Sama revisio sisältää eri tietoja")
            r.revision in c.ancestors -> MergeType.KEEP
            c.revision in r.ancestors && !c.deleted -> MergeType.UPDATE
            else -> MergeType.CONFLICT
        }
        MergeItem(r, c, type)
    }
}
fun resolve(item: MergeItem, useBackup: Boolean): Record {
    val c = item.current ?: return item.incoming
    val r = item.incoming
    return (if (useBackup) r else c).copy(revision = id(), ancestors = c.ancestors + r.ancestors + c.revision + r.revision)
}
