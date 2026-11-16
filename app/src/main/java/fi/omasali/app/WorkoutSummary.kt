package fi.omasali.app

/** Finished session status is independent of completed targets; stopping early is supported. */
data class WorkoutSummary(val completedExercises: Int, val totalExercises: Int, val completedSets: Int, val totalSets: Int, val skippedExercises: Int) {
    val progress: Float get() = if (totalExercises == 0) 0f else completedExercises.toFloat() / totalExercises
}
fun isComplete(e: WorkoutExercise): Boolean = !e.skipped && e.sets.size >= e.target.sets && e.sets.take(e.target.sets).all { it.done }
fun summarize(s: WorkoutSession) = WorkoutSummary(
    s.exercises.count(::isComplete), s.exercises.size,
    s.exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { it.done } },
    s.exercises.filterNot { it.skipped }.sumOf { maxOf(it.target.sets, it.sets.size) },
    s.exercises.count { it.skipped }
)
fun latestFinished(sessions: List<WorkoutSession>): WorkoutSession? = sessions.filter { it.status == Status.FINISHED }.maxWithOrNull(compareBy<WorkoutSession> { it.ended ?: it.started }.thenBy { it.started }.thenBy { it.id })
data class WorkoutSections(val active: WorkoutExercise?, val pending: List<WorkoutExercise>, val complete: List<WorkoutExercise>, val skipped: List<WorkoutExercise>)
fun workoutSections(s: WorkoutSession, openedId: String?): WorkoutSections {
    val others = s.exercises.filterNot { it.id == openedId }
    return WorkoutSections(s.exercises.find { it.id == openedId }, others.filter { !it.skipped && !isComplete(it) }, others.filter(::isComplete), others.filter { it.skipped })
}
