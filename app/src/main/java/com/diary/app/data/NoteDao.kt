package com.diary.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface NoteDao {

    // Get latest notes for a profile
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND isLatest = 1 ORDER BY timestamp DESC")
    fun getNotesForProfile(profileId: Long): LiveData<List<Note>>

    // Get latest notes for a profile with search
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND isLatest = 1 AND (content LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' OR time LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchNotesInProfile(profileId: Long, query: String): LiveData<List<Note>>

    // Get latest notes for a profile on a specific date
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND isLatest = 1 AND date = :date ORDER BY timestamp DESC")
    fun getNotesForDate(profileId: Long, date: String): LiveData<List<Note>>

    // Get latest notes for a profile on a specific date with search
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND isLatest = 1 AND date = :date AND (content LIKE '%' || :query || '%' OR time LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchNotesForDate(profileId: Long, date: String, query: String): LiveData<List<Note>>

    // Get all versions of a note group
    @Query("SELECT * FROM notes WHERE noteGroupId = :groupId ORDER BY version DESC")
    suspend fun getNoteVersions(groupId: String): List<Note>

    // Get specific note by ID
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    // Get latest notes as list (for export)
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND isLatest = 1 ORDER BY timestamp DESC")
    suspend fun getNotesListForProfile(profileId: Long): List<Note>

    // Get ALL notes from ALL profiles including ALL versions (for complete export)
    @Query("SELECT * FROM notes ORDER BY profileId, noteGroupId, version DESC")
    suspend fun getAllNotesForExport(): List<Note>

    // Check for duplicate (same profile, date, time, content)
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND date = :date AND time = :time AND content = :content AND isLatest = 1 LIMIT 1")
    suspend fun findDuplicate(profileId: Long, date: String, time: String, content: String): Note?

    // Insert note
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    // Insert multiple notes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    // Update note
    @Update
    suspend fun updateNote(note: Note)

    // Delete note
    @Delete
    suspend fun deleteNote(note: Note)

    // Delete all notes in a profile
    @Query("DELETE FROM notes WHERE profileId = :profileId")
    suspend fun deleteAllNotesInProfile(profileId: Long)

    // Delete entire note group (all versions)
    @Query("DELETE FROM notes WHERE noteGroupId = :groupId")
    suspend fun deleteNoteGroup(groupId: String)

    // Profile operations
    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, name ASC")
    fun getAllProfiles(): LiveData<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, name ASC")
    suspend fun getAllProfilesList(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): Profile?

    @Query("SELECT * FROM profiles ORDER BY id ASC LIMIT 1")
    suspend fun getFirstAvailableProfile(): Profile?

    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteNotesByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM notes WHERE profileId = :profileId AND isLatest = 1")
    suspend fun getEntryCountForProfile(profileId: Long): Int

    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearAllDefaults()
}
