package com.diary.app.repository

import androidx.lifecycle.LiveData
import com.diary.app.data.Note
import com.diary.app.data.NoteDao
import com.diary.app.data.Profile
import java.util.UUID

class NoteRepository(private val noteDao: NoteDao) {

    fun getNotesForProfile(profileId: Long): LiveData<List<Note>> = 
        noteDao.getNotesForProfile(profileId)

    fun searchNotesInProfile(profileId: Long, query: String): LiveData<List<Note>> =
        noteDao.searchNotesInProfile(profileId, query)

    fun getNotesForDate(profileId: Long, date: String): LiveData<List<Note>> =
        noteDao.getNotesForDate(profileId, date)

    fun searchNotesForDate(profileId: Long, date: String, query: String): LiveData<List<Note>> =
        noteDao.searchNotesForDate(profileId, date, query)

    suspend fun getNoteVersions(groupId: String): List<Note> = 
        noteDao.getNoteVersions(groupId)

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun getNotesListForProfile(profileId: Long): List<Note> = 
        noteDao.getNotesListForProfile(profileId)

    suspend fun getAllNotesForExport(): List<Note> =
        noteDao.getAllNotesForExport()

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun updateNoteWithNewVersion(note: Note, newContent: String): Long {
        // Mark current version as not latest
        noteDao.updateNote(note.copy(isLatest = false))
        
        // Create new version
        val newVersion = note.copy(
            id = 0,
            version = note.version + 1,
            content = newContent,
            isLatest = true,
            editedAt = System.currentTimeMillis()
        )
        return noteDao.insertNote(newVersion)
    }

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun deleteNoteGroup(groupId: String) = noteDao.deleteNoteGroup(groupId)

    suspend fun deleteAllNotesInProfile(profileId: Long) = noteDao.deleteAllNotesInProfile(profileId)

    suspend fun insertAll(notes: List<Note>) = noteDao.insertAll(notes)

    suspend fun findDuplicate(profileId: Long, date: String, time: String, content: String): Note? =
        noteDao.findDuplicate(profileId, date, time, content)

    // Profile operations
    fun getAllProfiles(): LiveData<List<Profile>> = noteDao.getAllProfiles()

    suspend fun getAllProfilesList(): List<Profile> = noteDao.getAllProfilesList()

    suspend fun getFirstAvailableProfile(): Profile? = noteDao.getFirstAvailableProfile()

    suspend fun getProfileById(id: Long): Profile? = noteDao.getProfileById(id)

    suspend fun getDefaultProfile(): Profile? = noteDao.getDefaultProfile()

    suspend fun insertProfile(profile: Profile): Long = noteDao.insertProfile(profile)

    suspend fun deleteProfile(profile: Profile) = noteDao.deleteProfile(profile)

    suspend fun updateProfile(profile: Profile) = noteDao.updateProfile(profile)

    suspend fun getEntryCountForProfile(profileId: Long): Int = noteDao.getEntryCountForProfile(profileId)

    suspend fun deleteNotesByIds(ids: List<Long>) = noteDao.deleteNotesByIds(ids)

    suspend fun setDefaultProfile(profile: Profile) {
        noteDao.clearAllDefaults()
        noteDao.updateProfile(profile.copy(isDefault = true))
    }

    fun generateNoteGroupId(): String = "note_${UUID.randomUUID()}"
}
