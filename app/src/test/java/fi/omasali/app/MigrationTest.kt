package fi.omasali.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @Test fun migrationPreservesLargePayloadAndShrinkingWriteRemovesOldChunks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = context.getDatabasePath("migration.db"); context.deleteDatabase("migration.db"); path.parentFile!!.mkdirs()
        // >2 MiB aggregate simulates a session with several photos, above typical CursorWindow size.
        val r = record("large", "exercise", Exercise(id = "large", name = "Säilyvä äö", notes = "a".repeat(2_500_000)))
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE records (id TEXT NOT NULL PRIMARY KEY, json TEXT NOT NULL)")
            old.execSQL("INSERT INTO records VALUES (?, ?)", arrayOf(r.id, codec.encodeToString(r)))
            old.version = 1
        }
        val db = Room.databaseBuilder(context, SaliDatabase::class.java, "migration.db").addMigrations(MIGRATION_1_2).build()
        try {
            val repo = Repository(db)
            assertEquals(r, repo.all().single())
            assertTrue(db.records().all().size > 30)
            assertTrue(db.records().all().all { it.json.length <= CHUNK_CHARS })
            repo.save(record("large", "exercise", Exercise(id = "large", name = "Pieni")))
            assertEquals(1, db.records().all().size)
            assertEquals("Pieni", repo.all().single().value<Exercise>().name)
        } finally { db.close(); context.deleteDatabase("migration.db") }
    }
}
