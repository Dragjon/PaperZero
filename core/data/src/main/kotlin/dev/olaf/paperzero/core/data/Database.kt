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
import androidx.room.Transaction
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

/** Time spent on a science's questions on a day (the phone's own day, "2026-10-31"). */
@Entity(tableName = "study", primaryKeys = ["day", "subject"])
data class StudyEntity(val day: String, val subject: String, val ms: Long)

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

    /** An unfinished timed set (an Exam sitting of no one paper) called [title], to carry on with. */
    @Query("SELECT * FROM sessions WHERE paperId IS NULL AND mode = 'Exam' AND title = :title AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun unfinishedTimed(title: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insert(answer: AnswerEntity)

    @Query("SELECT * FROM answers WHERE sessionId = :sessionId ORDER BY at")
    suspend fun answers(sessionId: Long): List<AnswerEntity>

    /** The answer given last to [key] in [sessionId], if any. */
    @Query("SELECT * FROM answers WHERE sessionId = :sessionId AND questionKey = :key ORDER BY at DESC LIMIT 1")
    suspend fun lastAnswer(sessionId: Long, key: String): AnswerEntity?

    /** The last wrong answer ever given to [key], if any. */
    @Query("SELECT * FROM answers WHERE questionKey = :key AND correct = 0 ORDER BY at DESC LIMIT 1")
    suspend fun lastWrong(key: String): AnswerEntity?

    @Query("DELETE FROM answers WHERE id = :id")
    suspend fun deleteAnswer(id: Long)

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

    @Query("INSERT OR IGNORE INTO study (day, subject, ms) VALUES (:day, :subject, 0)")
    suspend fun startStudy(day: String, subject: String)

    @Query("UPDATE study SET ms = ms + :ms WHERE day = :day AND subject = :subject")
    suspend fun addStudy(day: String, subject: String, ms: Long)

    @Transaction
    suspend fun study(day: String, subject: String, ms: Long) {
        startStudy(day, subject)
        addStudy(day, subject, ms)
    }

    /** Time spent, every day and science, oldest first. */
    @Query("SELECT * FROM study ORDER BY day")
    fun study(): Flow<List<StudyEntity>>

    @Query("DELETE FROM answers")
    suspend fun deleteAllAnswers()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM study")
    suspend fun deleteAllStudy()

    @Query("DELETE FROM likes")
    suspend fun deleteAllLikes()

    @Query("DELETE FROM comments")
    suspend fun deleteAllComments()

    /** Every answer, sitting and second studied gone: a fresh start. Likes and comments stay. */
    @Transaction
    suspend fun resetProgress() {
        deleteAllAnswers()
        deleteAllSessions()
        deleteAllStudy()
    }

    @Transaction
    suspend fun deleteSaved() {
        deleteAllLikes()
        deleteAllComments()
    }
}

@Database(
    entities = [SessionEntity::class, AnswerEntity::class, LikeEntity::class, CommentEntity::class, StudyEntity::class],
    version = 3,
    exportSchema = true,
)
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

        /** 3: time spent, by day and science. */
        private val THREE = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `study` (`day` TEXT NOT NULL, `subject` TEXT NOT NULL, `ms` INTEGER NOT NULL, PRIMARY KEY(`day`, `subject`))")
            }
        }

        fun create(context: Context): PaperDatabase =
            Room.databaseBuilder(context, PaperDatabase::class.java, "paperzero.db").addMigrations(TWO, THREE).build()
    }
}
