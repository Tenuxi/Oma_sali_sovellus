package fi.omasali.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString

// Keep every SQLite cursor cell well below Android's CursorWindow size, even with photos.
const val CHUNK_CHARS = 64000
@Entity(tableName = "record_chunks", primaryKeys = ["recordId", "part"])
data class StoredRecord(val recordId: String, val part: Int, val json: String)
fun encodeRows(record: Record): List<StoredRecord> {
    val text = codec.encodeToString(record)
    val rows = mutableListOf<StoredRecord>()
    var start = 0
    while (start < text.length) {
        var end = (start + CHUNK_CHARS).coerceAtMost(text.length)
        // SQLite encodes each cell as UTF-8: do not split an emoji's UTF-16 surrogate pair.
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        rows += StoredRecord(record.id, rows.size, text.substring(start, end))
        start = end
    }
    return rows
}
fun decodeRows(rows: List<StoredRecord>): List<Record> = rows.groupBy { it.recordId }.map { (_, parts) ->
    val sorted = parts.sortedBy { it.part }
    require(sorted.map { it.part } == sorted.indices.toList()) { "Tietokannan tietue on puutteellinen" }
    codec.decodeFromString<Record>(sorted.joinToString("") { it.json })
}
@Dao interface RecordDao {
    @Query("SELECT * FROM record_chunks ORDER BY recordId, part") fun observe(): Flow<List<StoredRecord>>
    @Query("SELECT * FROM record_chunks ORDER BY recordId, part") suspend fun all(): List<StoredRecord>
    @Query("DELETE FROM record_chunks WHERE recordId = :id") suspend fun removeParts(id: String)
    @Insert suspend fun put(rows: List<StoredRecord>)
}
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS record_chunks (recordId TEXT NOT NULL, part INTEGER NOT NULL, json TEXT NOT NULL, PRIMARY KEY(recordId, part))")
        // Split in SQL, so migration itself never reads a large photo aggregate into a CursorWindow.
        db.execSQL("""INSERT INTO record_chunks (recordId, part, json)
            WITH RECURSIVE chunks(recordId, part, remainder, chunk) AS (
                SELECT id, 0, substr(json, 64001), substr(json, 1, 64000) FROM records
                UNION ALL
                SELECT recordId, part + 1, substr(remainder, 64001), substr(remainder, 1, 64000)
                FROM chunks WHERE length(remainder) > 0
            ) SELECT recordId, part, chunk FROM chunks""")
        db.execSQL("DROP TABLE records")
    }
}
@Database(entities = [StoredRecord::class], version = 2, exportSchema = true)
abstract class SaliDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, SaliDatabase::class.java, "oma-sali.db").addMigrations(MIGRATION_1_2).build()
    }
}
