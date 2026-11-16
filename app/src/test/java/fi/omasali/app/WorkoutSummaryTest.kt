package fi.omasali.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutSummaryTest {
    private val exercise = Exercise(name = "Kyykky")
    private fun session(): WorkoutSession = WorkoutSession(programId="p",programName="P",dayId="d",dayName="Jalat",started=1000,ended=2000,status=Status.FINISHED,cycleOrder=1,cycleDays=listOf("d"),exercises=List(3) { index -> WorkoutExercise(exercise=exercise,target=ProgramExercise(exerciseId=exercise.id),sets=List(3) { set -> WorkoutSet(grams=if(set==0) 6000 else 5000,reps=if(set==0) 3 else 2,done=index==0 && set<2) }) })
    @Test fun partialFinishedSessionHasTwoSetsAndZeroCompletedExercises() {
        val s=session(); val summary=summarize(s)
        assertEquals(0,summary.completedExercises); assertEquals(3,summary.totalExercises)
        assertEquals(2,summary.completedSets); assertEquals(9,summary.totalSets); assertEquals(0f,summary.progress)
        assertEquals(28000L,s.exercises.sumOf(::volume))
        assertEquals(s,latestFinished(listOf(s,s.copy(id="active",started=5000,ended=null,status=Status.ACTIVE))))
    }
    @Test fun removedSetsDoNotSilentlyLowerTargetButExplicitTargetEditCan() {
        val e=session().exercises.first()
        assertFalse(isComplete(e.copy(sets=e.sets.take(2))))
        assertTrue(isComplete(e.copy(target=e.target.copy(sets=2))))
        assertFalse(isComplete(e.copy(target=e.target.copy(sets=2),skipped=true)))
        val s=session().copy(exercises=listOf(e.copy(target=e.target.copy(sets=2))))
        assertEquals(1f,summarize(s).progress)
    }
    @Test fun identicalExerciseNamesDoNotDuplicateTheOpenedInstance() {
        val s=session(); val id=s.exercises.last().id; val parts=workoutSections(s,id)
        assertEquals(id,parts.active!!.id)
        val closed=parts.pending+parts.complete+parts.skipped
        assertTrue(closed.none { it.id==id }); assertEquals(2,closed.size)
        assertEquals(3,(closed+parts.active!!).map { it.id }.distinct().size)
    }
    @Test fun correctedHistoryRecalculatesSetsVolumeAndRecords() {
        val old=session().copy(id="old",started=100,ended=200)
        val s=session(); val corrected=s.copy(exercises=s.exercises.mapIndexed { i,e -> if(i==0) e.copy(sets=e.sets.mapIndexed { j,set -> if(j==0) set.copy(grams=7000,reps=4) else set.copy(done=false) }) else e })
        assertEquals(1,summarize(corrected).completedSets)
        assertEquals(listOf(1000L to 28.0),metricPoints(listOf(corrected),exercise.id,Metric.VOLUME))
        assertNotNull(newRecordSummary(corrected,listOf(old)))
        val low=corrected.copy(exercises=corrected.exercises.map { it.copy(sets=it.sets.map { set -> set.copy(grams=3000) }) })
        assertNull(newRecordSummary(low,listOf(old)))
        assertEquals(listOf(1000L to 3.0),metricPoints(listOf(low),exercise.id,Metric.WEIGHT))
    }
    @Test fun cachedDraftCannotMaskAHistoryCorrectionAndLegacyTextIsRetained() {
        val old=WorkoutSet(grams=6000,reps=3,done=true)
        val draft=SetDraft(old)
        assertTrue(isDraftCurrent(draft,old,Load.EXTERNAL))
        assertFalse(isDraftCurrent(draft,old.copy(grams=3000),Load.EXTERNAL))
        val legacy=codec.decodeFromString<SetDraft>("""{"weight":"6","reps":"3","body":"","added":"0","assistance":"0","done":true}""")
        assertTrue(isDraftCurrent(legacy,old,Load.EXTERNAL))
        assertFalse(isDraftCurrent(legacy,old.copy(done=false),Load.EXTERNAL))
        assertEquals("6",legacy.weight)
    }
}
