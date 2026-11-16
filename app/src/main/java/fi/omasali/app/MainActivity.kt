package fi.omasali.app

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class SaliViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(SaliDatabase.open(app))
    val rows = repo.records.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var error by mutableStateOf<String?>(null)
    var ready by mutableStateOf(false)
    var ui by mutableStateOf(UiState())
        private set
    private var liveDrafts by mutableStateOf(emptySet<String>())
    private val uiStore = UiStateStore(app.preferences)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.initialize(); val saved = uiStore.read(); withContext(Dispatchers.Main) { ui = saved; ready = true } }
            catch (e: Exception) { withContext(Dispatchers.Main) { error = "Tietokantaa ei voitu avata: ${e.message}" } }
            for (command in commands) try { command() } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) { error = e.message ?: "Toiminto epäonnistui" }
            }
        }
    }
    fun run(block: suspend () -> Unit) { commands.trySend(block) }
    fun updateUi(change: (UiState) -> UiState) {
        ui = change(ui)
        run { uiStore.save(withContext(Dispatchers.Main) { ui }) }
    }
    fun openExercise(sessionId: String, exerciseId: String?) = updateUi { it.copy(opened = it.opened + (sessionId to (exerciseId ?: "summary"))) }
    fun stats(change: (StatsSelection) -> StatsSelection) = updateUi { it.copy(stats = change(it.stats)) }
    fun draftFor(set: WorkoutSet, load: Load): SetDraft = ui.drafts[set.id]?.takeIf { set.id in liveDrafts || isDraftCurrent(it, set, load) } ?: SetDraft(set)
    fun hasOlderDraft(set: WorkoutSet, load: Load) = ui.drafts[set.id]?.let { set.id !in liveDrafts && !isDraftCurrent(it, set, load) } ?: false
    fun editSet(sessionId: String, exerciseId: String, set: WorkoutSet, load: Load, draft: SetDraft) {
        val input = draft.copy(base = set)
        liveDrafts = liveDrafts + set.id
        updateUi { it.copy(drafts = it.drafts + (set.id to input)) }
        val updated = runCatching { input.applyTo(set, load) }.getOrNull() ?: return
        run {
            repo.updateSession(sessionId) { s -> s.copy(exercises = s.exercises.map { e -> if (e.id == exerciseId) e.copy(sets = e.sets.map { if (it.id == set.id) updated else it }) else e }) }
        }
    }
    fun save(r: Record) = run { repo.save(r) }
    fun program(id: String, f: (TrainingProgram) -> TrainingProgram) = run { repo.updateProgram(id, f) }
    fun session(id: String, f: (WorkoutSession) -> WorkoutSession) = run {
        var changed = emptySet<String>()
        repo.updateSession(id) { before ->
            val after = f(before)
            val newSets = after.exercises.flatMap { it.sets }.associateBy { it.id }
            changed = before.exercises.flatMap { it.sets }.filter { newSets[it.id] != it }.map { it.id }.toSet()
            after
        }
        withContext(Dispatchers.Main) { liveDrafts = liveDrafts - changed }
    }
    fun exercise(sessionId: String, id: String, f: (WorkoutExercise) -> WorkoutExercise) = session(sessionId) { s -> s.copy(exercises = s.exercises.map { if (it.id == id) f(it) else it }) }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: SaliViewModel = viewModel()
            val rows by vm.rows.collectAsState()
            val settings = rows.find { it.id == "settings" }?.value<Settings>() ?: Settings()
            MaterialTheme(colorScheme = if (settings.dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB5E875)) else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF426900))) {
                Surface { if (vm.ready) SaliApp(vm, rows, settings) else Text("Avataan OMA SALI…") }
                vm.error?.let { text -> AlertDialog(onDismissRequest = { vm.error = null }, title = { Text("Toiminto ei onnistunut") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { vm.error = null }) { Text("Sulje") } }) }
            }
        }
    }
}
