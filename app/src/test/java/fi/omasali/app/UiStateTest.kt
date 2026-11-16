package fi.omasali.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class UiStateTest {
    @Test fun preferencesAndPartialInputsSurviveStoreRestartWithoutMixingWorkouts() = runBlocking {
        val file = Files.createTempDirectory("sali-ui").resolve("ui.preferences_pb").toFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = UiStateStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        val draft = SetDraft("62,", "", "", "0", "0", false)
        val state = UiState(opened = mapOf("workout-a" to "exercise-a", "workout-b" to "exercise-b"), drafts = mapOf("set-a" to draft), stats = StatsSelection("body", "exercise-a", "reps", 3, "60"))
        first.save(state)
        firstScope.coroutineContext[Job]!!.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = UiStateStore(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(state, second.read())
            assertThrows(Exception::class.java) { draft.applyTo(WorkoutSet(), Load.EXTERNAL) }
            assertEquals(62500L, draft.copy(weight = "62,5", reps = "12", done = true).applyTo(WorkoutSet(), Load.EXTERNAL).grams)
        } finally { secondScope.coroutineContext[Job]!!.cancelAndJoin(); file.delete(); file.parentFile.delete() }
    }
    private fun ex(done: Boolean = false) = Exercise(name = "Testi").let { WorkoutExercise(exercise = it, target = ProgramExercise(exerciseId = it.id), sets = List(3) { WorkoutSet(grams = 60000, reps = 10, done = done) }) }
    private fun session(exercises: List<WorkoutExercise>, started: Long = 1000, status: Status = Status.ACTIVE) = WorkoutSession(programId = "p", programName = "P", dayId = "d", dayName = "D", started = started, status = status, exercises = exercises, cycleOrder = 1, cycleDays = listOf("d"))
    @Test fun openingAnotherExerciseNeverCompletesItAndLastDoesNotWrap() {
        val a = ex(); val b = ex(true); val c = ex(); val s = session(listOf(a,b,c))
        assertEquals(a.id, initialOpen(s, null)); assertEquals(c.id, nextOpen(s,a.id)); assertNull(nextOpen(s,c.id))
        assertEquals(a.id, initialOpen(s,"deleted")); assertNull(initialOpen(s,"summary"))
        assertFalse(isComplete(a)); assertFalse(isComplete(a.copy(sets = emptyList()))); assertTrue(isComplete(b)); assertFalse(isComplete(b.copy(skipped = true)))
        assertFalse(isComplete(a.copy(sets = a.sets.mapIndexed { i, set -> set.copy(done = i == 0) })))
    }
    @Test fun chartsKeepCompletedActiveSetsAndLoadRestrictions() {
        val ex = ex(true); val active = session(listOf(ex)); val cancelled = session(listOf(ex), 2000, Status.CANCELLED)
        assertEquals(listOf(1000L to 60.0), metricPoints(listOf(active,cancelled),ex.exercise.id,Metric.WEIGHT))
        assertEquals(listOf(1000L to 1800.0), metricPoints(listOf(active),ex.exercise.id,Metric.VOLUME))
        assertTrue(metricPoints(listOf(active.copy(exercises = listOf(ex.copy(skipped = true)))),ex.exercise.id,Metric.VOLUME).isEmpty())
        val assisted = ex.copy(exercise = ex.exercise.copy(load = Load.ASSISTED), sets = ex.sets.map { it.copy(grams = 0, body = 80000, added = 5000) })
        assertTrue(metricPoints(listOf(session(listOf(assisted))),ex.exercise.id,Metric.RM).isEmpty())
        assertEquals(listOf(1000L to 5.0),metricPoints(listOf(session(listOf(assisted))),ex.exercise.id,Metric.ADDED))
        assertEquals(listOf(Metric.REPS),availableMetrics(setOf(Load.BODY)))
    }
    @Test fun previousWorkoutRecordRequiresActualEarlierComparableResult() {
        val oldExercise=ex(true); val old=session(listOf(oldExercise),1000,Status.FINISHED)
        val current=session(listOf(oldExercise.copy(sets = oldExercise.sets.map { it.copy(grams=62500) })),2000,Status.FINISHED)
        assertNotNull(newRecordSummary(current,listOf(old,current)))
        assertNull(newRecordSummary(current,listOf(current)))
        assertNull(newRecordSummary(current.copy(exercises=listOf(oldExercise)),listOf(old)))
    }
}
