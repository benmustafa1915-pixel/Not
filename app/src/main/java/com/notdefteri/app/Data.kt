package com.notdefteri.app

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val pinned: Boolean = false,
    /** Bu kategoride hazır kelime-emoji sözlüğü çalışsın mı. */
    @ColumnInfo(defaultValue = "1") val emojiOn: Boolean = true
)

/** Kısayol: [trig] yazılıp boşluk bırakılınca [expansion] çıkar. appendMode: kelimeyi silmez, yanına ekler. */
@Entity(tableName = "shortcuts", indices = [Index(value = ["trig"], unique = true)])
data class Shortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trig: String,
    val expansion: String,
    val appendMode: Boolean = false,
    /** Hazır emoji sözlüğünden gelenler true. */
    val builtin: Boolean = false
)

@Entity(tableName = "notes", indices = [Index("categoryId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Düz metin. Kilitli notlarda boştur. */
    val body: String = "",
    /** Biçim bilgisi (JSON). Kilitli notlarda boştur. */
    val spans: String = "",
    /** Kilitli notların şifreli içeriği (Base64). */
    val cipher: String = "",
    val locked: Boolean = false,
    val categoryId: Long = 0,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val size: Int = 0,
    val preview: String = ""
)

/** Liste için hafif model: uzun metin yüklenmez, liste hızlı açılır. */
data class NoteItem(
    val id: Long,
    val title: String,
    val preview: String,
    val locked: Boolean,
    val categoryId: Long,
    val pinned: Boolean,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val size: Int
)

@Dao
interface NoteDao {
    @Query(
        "SELECT id, title, preview, locked, categoryId, pinned, favorite, createdAt, updatedAt, size " +
            "FROM notes WHERE (:cat = -1 OR categoryId = :cat) " +
            "AND (:q = '' OR title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%')"
    )
    fun items(cat: Long, q: String): Flow<List<NoteItem>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(n: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE notes SET pinned = :v WHERE id = :id")
    suspend fun setPinned(id: Long, v: Boolean)

    @Query("UPDATE notes SET favorite = :v WHERE id = :id")
    suspend fun setFavorite(id: Long, v: Boolean)

    @Query("UPDATE notes SET categoryId = :cat WHERE id = :id")
    suspend fun setCategory(id: Long, cat: Long)

    @Query("UPDATE notes SET categoryId = 0 WHERE categoryId = :cat")
    suspend fun clearCategory(cat: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY pinned DESC, name COLLATE NOCASE")
    fun all(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: Category): Long

    @Delete
    suspend fun delete(c: Category)

    @Query("UPDATE categories SET pinned = :v WHERE id = :id")
    suspend fun setPinned(id: Long, v: Boolean)

    @Query("UPDATE categories SET emojiOn = :v WHERE id = :id")
    suspend fun setEmoji(id: Long, v: Boolean)

    @Query("SELECT * FROM categories")
    suspend fun list(): List<Category>
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY builtin, trig COLLATE NOCASE")
    fun all(): Flow<List<Shortcut>>

    @Query("SELECT * FROM shortcuts")
    suspend fun list(): List<Shortcut>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Shortcut): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Shortcut>)

    @Delete
    suspend fun delete(s: Shortcut)
}

/** 1 → 2: kısayol tablosu ve kategori emoji bayrağı eklendi (mevcut notlara dokunulmaz). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN emojiOn INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shortcuts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trig` TEXT NOT NULL, `expansion` TEXT NOT NULL, `appendMode` INTEGER NOT NULL, " +
                "`builtin` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shortcuts_trig` ON `shortcuts` (`trig`)")
    }
}

@Database(entities = [Note::class, Category::class, Shortcut::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun categories(): CategoryDao
    abstract fun shortcuts(): ShortcutDao

    companion object {
        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "notes.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { inst = it }
        }
    }
}
