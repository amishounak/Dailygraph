package com.diary.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, Profile::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create profiles table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)

                // Insert default profile
                database.execSQL("""
                    INSERT INTO profiles (name, isDefault, createdAt) 
                    VALUES ('My Journal', 1, ${System.currentTimeMillis()})
                """)

                // Create new notes table with profile support
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        noteGroupId TEXT NOT NULL,
                        version INTEGER NOT NULL DEFAULT 1,
                        isLatest INTEGER NOT NULL DEFAULT 1,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        temperature TEXT NOT NULL,
                        location TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        editedAt INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES profiles(id) ON DELETE CASCADE
                    )
                """)

                // Migrate existing notes to default profile with new schema
                database.execSQL("""
                    INSERT INTO notes_new (id, profileId, noteGroupId, version, isLatest, date, time, temperature, location, content, timestamp, editedAt)
                    SELECT id, 1, 'note_' || id, 1, 1, date, time, temperature, location, content, timestamp, timestamp
                    FROM notes
                """)

                // Drop old table and rename new one
                database.execSQL("DROP TABLE notes")
                database.execSQL("ALTER TABLE notes_new RENAME TO notes")

                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_profileId ON notes(profileId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_noteGroupId ON notes(noteGroupId)")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "diary_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // For dev - remove in production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
