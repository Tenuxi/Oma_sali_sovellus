package fi.omasali.app

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val e = Exercise(name = "Penkki")
    private fun exercise(load: Load = Load.EXTERNAL) = WorkoutExercise(exercise = e.copy(load = load), target = ProgramExercise(exerciseId = e.id), sets = List(3) { WorkoutSet(grams = 60000, reps = 12, done = true) })
    @Test fun exactWeights() { assertEquals(62500L, grams("62,5")); assertEquals("62.5", kg(62500)); assertThrows(Exception::class.java) { grams("1.0001") } }
    @Test fun progressionRequiresEveryTargetAtSameWeight() {
        val ex = exercise(); assertEquals("Seuraavaksi 62.5 kg", suggestion(ex))
        assertNull(suggestion(ex.copy(sets = ex.sets.drop(1))))
        assertNull(suggestion(ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == 1) s.copy(grams = 55000) else s })))
        assertNull(suggestion(ex.copy(sets = ex.sets.mapIndexed { i, s -> if (i == 0) s.copy(done = false) else s })))
        assertNull(suggestion(ex.copy(sets = ex.sets.map { it.copy(reps = 11) })))
        assertNull(suggestion(ex.copy(skipped = true)))
    }
    @Test fun bodyAndAssistanceStaySeparate() {
        val ex = exercise(Load.ASSISTED).copy(sets = List(3) { WorkoutSet(reps = 12, body = 80000, assistance = 10000, done = true) })
        assertEquals("Seuraavaksi avustus 7.5 kg", suggestion(ex)); assertNull(oneRm(ex)); assertEquals(0L, volume(ex))
        assertEquals("Seuraavaksi lisäpaino 7.5 kg", suggestion(ex.copy(sets = ex.sets.map { it.copy(added = 5000, assistance = 0) })))
        assertNull(suggestion(ex.copy(sets = ex.sets.map { it.copy(added = 5000) })))
    }
    @Test fun onlyCompletedApplicableSetsCount() {
        val ex = exercise(); assertEquals(2160000L, volume(ex)); assertEquals(84.0, oneRm(ex)!!, 0.001)
        assertNull(oneRm(ex.copy(sets = ex.sets.map { it.copy(reps = 13) })))
        assertEquals(0L, volume(ex.copy(sets = ex.sets.map { it.copy(done = false) })))
        assertNull(oneRm(ex.copy(skipped = true)))
    }
    @Test fun cancellationReplaysRemainingStartsAndProgramIsolation() {
        val p = TrainingProgram(name = "P", days = listOf(ProgramDay(name = "A"), ProgramDay(name = "B"), ProgramDay(name = "C")))
        fun session(i: Int, order: Long) = WorkoutSession(programId = p.id, programName = p.name, dayId = p.days[i].id, dayName = p.days[i].name, started = 1000, exercises = emptyList(), cycleOrder = order, cycleDays = p.days.map { it.id })
        val a = session(0, 1); val b = session(1, 2)
        assertEquals(p.days[2], nextDay(p, listOf(a, b)))
        assertEquals(p.days[2], nextDay(p, listOf(a.copy(status = Status.CANCELLED), b)))
        assertEquals(p.days[1], nextDay(p, listOf(a, b.copy(status = Status.CANCELLED))))
        assertEquals(p.days[0], nextDay(p, listOf(a.copy(programId = "other"))))
    }
    @Test fun backupAncestryAndTombstones() {
        val a = record(e.id, "exercise", e)
        val b = a.copy(revision = id(), ancestors = setOf(a.revision), payload = codec.encodeToString(Exercise.serializer(), e.copy(name = "Uusi")))
        assertEquals(MergeType.UPDATE, preview(listOf(a), listOf(b)).single().type)
        assertEquals(MergeType.KEEP, preview(listOf(b), listOf(a)).single().type)
        val deleted = a.copy(deleted = true, revision = id(), ancestors = setOf(a.revision))
        assertEquals(MergeType.KEEP, preview(listOf(deleted), listOf(a)).single().type)
        val conflict = preview(listOf(deleted), listOf(b)).single()
        assertEquals(MergeType.CONFLICT, conflict.type)
        val resolved = resolve(conflict, false); assertTrue(resolved.deleted)
        assertEquals(MergeType.KEEP, preview(listOf(resolved), listOf(b)).single().type)
        assertEquals(MergeType.SAME, preview(listOf(a), listOf(a)).single().type)
        assertThrows(IllegalStateException::class.java) { preview(listOf(a), listOf(a.copy(deleted = true))) }
    }
}
