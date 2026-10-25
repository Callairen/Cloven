package app.cloven.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        TextBlockEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        ReadingSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ClovenDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "cloven.db"

        /**
         * Schema changes must ship an explicit Migration added here. Destructive fallback is
         * deliberately never enabled: an upgrade must not be able to wipe the library.
         */
        fun build(context: Context): ClovenDatabase =
            Room.databaseBuilder(context, ClovenDatabase::class.java, NAME)
                .addCallback(ForeignKeysOn)
                .build()

        fun inMemory(context: Context): ClovenDatabase =
            Room.inMemoryDatabaseBuilder(context, ClovenDatabase::class.java)
                .addCallback(ForeignKeysOn)
                .build()
    }

    private object ForeignKeysOn : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
