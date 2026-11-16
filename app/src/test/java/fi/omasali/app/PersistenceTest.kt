package fi.omasali.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.serialization.encodeToString

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceTest {
    private lateinit var db: SaliDatabase
    private lateinit var repo: Repository
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun setup() { context.deleteDatabase("test.db"); reopen() }
    private fun reopen() { db = Room.databaseBuilder(context, SaliDatabase::class.java, "test.db").build(); repo = Repository(db) }
    @After fun close() { db.close(); context.deleteDatabase("test.db") }
    @Test fun sessionsSurviveReopenProgramRemovalAndCorrections() = runTest {
        repo.initialize(); repo.template(2)
        val p = repo.all().first { it.kind == "program" }.value<TrainingProgram>()
        val a = repo.start(p.id, p.days[0].id); val b = repo.start(p.id, p.days[1].id)
        repo.updateSession(a) { s -> s.copy(exercises = s.exercises.mapIndexed { i, e -> if (i == 0) e.copy(sets = e.sets.map { it.copy(grams = 60000, reps = 12, done = true) }) else e }) }
        db.close(); reopen()
        var sessions = repo.all().filter { it.kind == "session" }.map { it.value<WorkoutSession>() }
        assertEquals(2, sessions.count { it.status == Status.ACTIVE })
        assertEquals(2160000L, volume(sessions.first { it.id == a }.exercises.first()))
        repo.updateSession(a) { it.copy(status = Status.CANCELLED) }
        sessions = repo.all().filter { it.kind == "session" }.map { it.value<WorkoutSession>() }
        assertEquals(p.days[0], nextDay(p, sessions))
        repo.updateSession(b) { it.copy(status = Status.FINISHED, ended = it.started + 1000) }
        repo.updateSession(b) { it.copy(started = it.started - 10000, notes = "Korjattu") }
        repo.delete(p.id)
        assertEquals(2, repo.all().count { it.kind == "session" })
        assertEquals("Korjattu", repo.all().first { it.id == b }.value<WorkoutSession>().notes)
    }
    @Test fun backupRoundTripIdempotenceConflictAndAtomicFailure() = runTest {
        repo.initialize(); repo.template(3)
        val p = repo.all().first { it.kind == "program" }.value<TrainingProgram>(); repo.start(p.id, p.days.first().id)
        val backup = parseBackup(repo.export()); val before = repo.all()
        repo.merge(before, backup, emptyMap()); assertEquals(before, repo.all())
        repo.updateProgram(p.id) { it.copy(name = "Paikallinen") }
        val incoming = backup.records.map { if (it.id == p.id) it.copy(revision = id(), ancestors = it.ancestors + it.revision, payload = codec.encodeToString(p.copy(name = "Tuotu"))) else it }
        val local = repo.all(); val conflict = preview(local, incoming).single { it.incoming.id == p.id }; assertEquals(MergeType.CONFLICT, conflict.type)
        repo.merge(local, Backup(records = incoming), mapOf(p.id to true))
        val merged = repo.all(); assertEquals("Tuotu", merged.first { it.id == p.id }.value<TrainingProgram>().name)
        repo.merge(merged, Backup(records = incoming), emptyMap()); assertEquals(merged, repo.all())
        val bad = Backup(records = listOf(record("bad", "weight", BodyWeightEntry(id = "bad", grams = -1, time = 1))))
        try { repo.merge(merged, bad, emptyMap()); fail("Invalid import accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(merged, repo.all())
        try { repo.merge(local, backup, emptyMap()); fail("Stale preview accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(merged, repo.all())
    }
    @Test fun malformedBackupsRejected() {
        assertThrows(Exception::class.java) { parseBackup("{}") }
        assertThrows(Exception::class.java) { parseBackup(codec.encodeToString(Backup(metadata = BackupMetadata(format = 999), records = emptyList()))) }
        val e = Exercise(name = "Testi"); val r = record(e.id, "exercise", e)
        assertThrows(Exception::class.java) { parseBackup(codec.encodeToString(Backup(records = listOf(r, r)))) }
    }
}
