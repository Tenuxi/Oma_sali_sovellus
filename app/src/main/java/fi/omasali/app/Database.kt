package fi.omasali.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records")
data class StoredRecord(@PrimaryKey val id: String, val json: String)
@Dao interface RecordDao {
    @Query("SELECT * FROM records ORDER BY id") fun observe(): Flow<List<StoredRecord>>
    @Query("SELECT * FROM records ORDER BY id") suspend fun all(): List<StoredRecord>
    @Upsert suspend fun put(rows: List<StoredRecord>)
}
@Database(entities = [StoredRecord::class], version = 1, exportSchema = true)
abstract class SaliDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, SaliDatabase::class.java, "oma-sali.db").build()
    }
}
