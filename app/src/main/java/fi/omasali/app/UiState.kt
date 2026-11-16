package fi.omasali.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class StatsSelection(val section: String = "exercises", val exerciseId: String? = null, val metric: String = "weight", val months: Int = 0, val fixedWeight: String = "")
@Serializable data class SetDraft(val weight: String, val reps: String, val body: String, val added: String, val assistance: String, val done: Boolean) {
    constructor(s: WorkoutSet) : this(kg(s.grams), "${s.reps}", s.body?.let(::kg) ?: "", kg(s.added), kg(s.assistance), s.done)
    fun applyTo(s: WorkoutSet, load: Load): WorkoutSet {
        val n = reps.toInt(); require(n in 0..1000 && (!done || n > 0))
        val b = if (load == Load.EXTERNAL) null else body.takeIf { it.isNotBlank() }?.let(::grams)
        require(b == null || b > 0)
        val a = if (load == Load.ASSISTED) grams(added) else 0L
        val help = if (load == Load.ASSISTED) grams(assistance) else 0L
        require(a == 0L || help == 0L)
        return s.copy(grams = if (load == Load.EXTERNAL) grams(weight) else 0, reps = n, body = b, added = a, assistance = help, done = done)
    }
}
@Serializable data class UiState(val opened: Map<String, String> = emptyMap(), val drafts: Map<String, SetDraft> = emptyMap(), val stats: StatsSelection = StatsSelection())
class UiStateStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("ui_state_v1_1")
    suspend fun read(): UiState = store.data.first()[key]?.let { codec.decodeFromString<UiState>(it) } ?: UiState()
    suspend fun save(state: UiState) { store.edit { it[key] = codec.encodeToString(state) } }
}
fun isComplete(e: WorkoutExercise) = !e.skipped && e.sets.isNotEmpty() && e.sets.all { it.done }
fun initialOpen(s: WorkoutSession, saved: String?): String? = when {
    saved == "summary" -> null
    s.exercises.any { it.id == saved } -> saved
    else -> s.exercises.firstOrNull { !it.skipped && !isComplete(it) }?.id
}
fun nextOpen(s: WorkoutSession, current: String): String? = s.exercises.drop(s.exercises.indexOfFirst { it.id == current } + 1).firstOrNull { !it.skipped && !isComplete(it) }?.id
fun newRecordSummary(session: WorkoutSession, history: List<WorkoutSession>): String? {
    val earlier = history.filter { it.id != session.id && it.status != Status.CANCELLED && it.started < session.started }
    return session.exercises.firstNotNullOfOrNull { e ->
        if (e.skipped || e.exercise.load != Load.EXTERNAL) return@firstNotNullOfOrNull null
        val best = e.sets.filter { it.done }.maxOfOrNull { it.grams } ?: return@firstNotNullOfOrNull null
        val old = earlier.flatMap { it.exercises }.filter { it.exercise.id == e.exercise.id && it.exercise.load == Load.EXTERNAL && !it.skipped }.flatMap { it.sets }.filter { it.done }.maxOfOrNull { it.grams }
        if (old != null && best > old) "Uusi painoennätys: ${e.exercise.name} ${kg(best)} kg" else null
    }
}
