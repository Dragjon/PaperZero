package dev.olaf.paperzero.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** How a set of questions is done. */
enum class Mode {
    /** Each answer marked as soon as it's given, with its explanation. */
    Practice,

    /** A whole paper against the clock; marked at the end. */
    Exam,
}

/** One sitting: a paper, ten random questions, or mistakes to fix. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    /** The paper, when it's a whole paper. */
    val paperId: String?,
    val title: String,
    /** The questions, as "paper/number" keys, in the order they're asked. */
    val keys: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    /** Time spent answering, not counting time away from the app. */
    val spentMs: Long = 0,
    val score: Int = 0,
    val total: Int = 0,
)

/** An answer given. The latest for a question in a session is the one that counts. */
@Entity(tableName = "answers", indices = [Index("questionKey"), Index("sessionId")])
data class AnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionKey: String,
    val chosen: String,
    val correct: Boolean,
    val at: Long,
)

/** A question liked, to find again. */
@Entity(tableName = "likes")
data class LikeEntity(@PrimaryKey val questionKey: String, val at: Long)

/** A comment on a question: a note to self, kept on the phone and seen by no one else. */
@Entity(tableName = "comments", indices = [Index("questionKey")])
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionKey: String,
    val text: String,
    val at: Long,
)

@Dao
interface PaperDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("UPDATE sessions SET finishedAt = :at, score = :score, total = :total WHERE id = :id")
    suspend fun finish(id: Long, at: Long, score: Int, total: Int)

    @Query("UPDATE sessions SET spentMs = spentMs + :ms WHERE id = :id")
    suspend fun addTime(id: Long, ms: Long)

    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    fun finished(): Flow<List<SessionEntity>>

    /** A paper's unfinished sitting, to carry on with. */
    @Query("SELECT * FROM sessions WHERE paperId = :paperId AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinished(paperId: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insert(answer: AnswerEntity)

    @Query("SELECT * FROM answers WHERE sessionId = :sessionId ORDER BY at")
    suspend fun answers(sessionId: Long): List<AnswerEntity>

    @Query("DELETE FROM answers WHERE sessionId = :sessionId")
    suspend fun deleteAnswers(sessionId: Long)

    /** Every answer ever given, oldest first: for progress and for what still needs work. */
    @Query("SELECT * FROM answers ORDER BY at")
    fun allAnswers(): Flow<List<AnswerEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun like(like: LikeEntity)

    @Query("DELETE FROM likes WHERE questionKey = :key")
    suspend fun unlike(key: String)

    /** Liked questions, the newest like first. */
    @Query("SELECT * FROM likes ORDER BY at DESC")
    fun likes(): Flow<List<LikeEntity>>

    @Insert
    suspend fun insert(comment: CommentEntity): Long

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteComment(id: Long)

    /** Every comment, oldest first. */
    @Query("SELECT * FROM comments ORDER BY at")
    fun comments(): Flow<List<CommentEntity>>
}

@Database(entities = [SessionEntity::class, AnswerEntity::class, LikeEntity::class, CommentEntity::class], version = 2, exportSchema = true)
abstract class PaperDatabase : RoomDatabase() {
    abstract fun dao(): PaperDao

    companion object {
        /** 2: likes and comments. */
        private val TWO = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `likes` (`questionKey` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`questionKey`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `comments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `questionKey` TEXT NOT NULL, `text` TEXT NOT NULL, `at` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_comments_questionKey` ON `comments` (`questionKey`)")
            }
        }

        fun create(context: Context): PaperDatabase =
            Room.databaseBuilder(context, PaperDatabase::class.java, "paperzero.db").addMigrations(TWO).build()
    }
}
