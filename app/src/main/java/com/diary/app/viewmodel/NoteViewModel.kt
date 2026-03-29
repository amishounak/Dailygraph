package com.diary.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.*
import com.diary.app.data.Note
import com.diary.app.data.NoteDatabase
import com.diary.app.data.Profile
import com.diary.app.repository.NoteRepository
import com.diary.app.utils.ProfilePreferences
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    private val profilePrefs = ProfilePreferences(application)

    // Current profile — guaranteed non-null at usage sites; suppress lint false positive
    @SuppressLint("NullSafeMutableLiveData")
    private val _currentProfile = MutableLiveData<Profile>()
    val currentProfile: LiveData<Profile> = _currentProfile

    // Search and filter state
    private val _searchQuery = MutableLiveData("")
    private val _selectedDate = MutableLiveData<String?>(null)
    val selectedDate: LiveData<String?> = _selectedDate

    // Displayed notes based on profile, search, and date filter
    val displayedNotes: LiveData<List<Note>>

    // All profiles
    val allProfiles: LiveData<List<Profile>>

    init {
        val dao = NoteDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(dao)
        allProfiles = repository.getAllProfiles()

        // Load current profile
        viewModelScope.launch {
            loadCurrentProfile()
        }

        // Combined filtering: profile + search + date
        displayedNotes = _currentProfile.switchMap { profile ->
            if (profile == null) {
                MutableLiveData(emptyList())
            } else {
                _selectedDate.switchMap { date ->
                    _searchQuery.switchMap { query ->
                        when {
                            date != null && query.isNotBlank() ->
                                repository.searchNotesForDate(profile.id, date, query)
                            date != null ->
                                repository.getNotesForDate(profile.id, date)
                            query.isNotBlank() ->
                                repository.searchNotesInProfile(profile.id, query)
                            else ->
                                repository.getNotesForProfile(profile.id)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NullSafeMutableLiveData")
    private suspend fun loadCurrentProfile() {
        val profileId = profilePrefs.currentProfileId
        var profile = repository.getProfileById(profileId)
        
        // If saved profile doesn't exist, try the default profile
        if (profile == null) {
            profile = repository.getDefaultProfile()
        }

        // If no default, get first available
        if (profile == null) {
            val allProfiles = repository.getAllProfilesList()
            profile = allProfiles.firstOrNull()
        }
            
        // If no profiles exist at all, create the first one
        if (profile == null) {
            profile = createDefaultProfile()
        }
        
        profilePrefs.currentProfileId = profile!!.id
        _currentProfile.postValue(profile)

        // Ensure exactly one profile is marked default
        val defaultExists = repository.getDefaultProfile() != null
        if (!defaultExists) {
            repository.setDefaultProfile(profile)
        }
    }

    private suspend fun createDefaultProfile(): Profile {
        val defaultProfile = Profile(name = "Dailygraph", isDefault = true)
        val id = repository.insertProfile(defaultProfile)
        profilePrefs.currentProfileId = id
        return defaultProfile.copy(id = id)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateFilter(date: String?) {
        _selectedDate.value = date
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedDate.value = null
    }

    fun switchProfile(profile: Profile) {
        _currentProfile.value = profile
        profilePrefs.currentProfileId = profile.id
        clearFilters() // Reset filters when switching profiles
    }

    fun insertNote(note: Note, onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertNote(note)
            onSuccess(id)
        }
    }

    fun updateNoteContent(note: Note, newContent: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateNoteWithNewVersion(note, newContent)
            onSuccess()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun deleteNoteGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteNoteGroup(groupId)
        }
    }

    fun deleteMultipleNotes(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { repository.deleteNote(it) }
        }
    }

    suspend fun getNoteVersions(groupId: String): List<Note> =
        repository.getNoteVersions(groupId)

    suspend fun getNoteById(id: Long): Note? =
        repository.getNoteById(id)

    suspend fun getNotesListForExport(): List<Note> {
        val profile = _currentProfile.value ?: return emptyList()
        return repository.getNotesListForProfile(profile.id)
    }

    suspend fun getAllNotesForExport(): List<Note> {
        // Export ALL notes from ALL profiles (including all versions)
        return repository.getAllNotesForExport()
    }

    suspend fun getAllProfilesForExport(): List<Profile> {
        return repository.getAllProfilesList()
    }

    fun importNotes(notes: List<Note>, replaceAll: Boolean, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val profile = _currentProfile.value ?: return@launch

            if (replaceAll) {
                repository.deleteAllNotesInProfile(profile.id)
            }

            var imported = 0
            var duplicates = 0

            for (note in notes) {
                // Check for duplicates
                val duplicate = repository.findDuplicate(
                    profile.id,
                    note.date,
                    note.time,
                    note.content
                )
                if (duplicate == null) {
                    repository.insertNote(note.copy(profileId = profile.id))
                    imported++
                } else {
                    duplicates++
                }
            }

            onComplete(imported, duplicates)
        }
    }

    fun importFullData(fullImport: com.diary.app.utils.ImportExportHelper.FullImportData, onComplete: (Int, Int) -> Unit) {
        viewModelScope.launch {
            // Create profile ID mapping (old ID -> new ID)
            val profileIdMap = mutableMapOf<Long, Long>()
            
            // Import profiles
            for (profileData in fullImport.profiles) {
                // Check if profile with same name exists
                val existingProfiles = repository.getAllProfilesList()
                val existing = existingProfiles.find { it.name == profileData.name }
                
                if (existing != null) {
                    // Use existing profile
                    profileIdMap[profileData.id] = existing.id
                } else {
                    // Create new profile
                    val newProfile = com.diary.app.data.Profile(
                        name = profileData.name,
                        isDefault = false  // Don't allow importing default profile
                    )
                    val newId = repository.insertProfile(newProfile)
                    profileIdMap[profileData.id] = newId
                }
            }
            
            // Group notes by noteGroupId to preserve version history
            val noteGroups = fullImport.notes.groupBy { it.noteGroupId }
            
            var imported = 0
            var duplicates = 0
            
            for ((groupId, notesInGroup) in noteGroups) {
                val newProfileId = profileIdMap[notesInGroup.first().profileId] ?: continue
                
                // Check latest version for duplicates
                val latestNote = notesInGroup.find { it.isLatest } ?: notesInGroup.maxByOrNull { it.version }
                
                if (latestNote != null) {
                    val duplicate = repository.findDuplicate(
                        newProfileId,
                        latestNote.date,
                        latestNote.time,
                        latestNote.content
                    )
                    
                    if (duplicate == null) {
                        // Import all versions of this note group
                        for (note in notesInGroup) {
                            repository.insertNote(note.copy(
                                id = 0,  // Let database generate new ID
                                profileId = newProfileId
                            ))
                            imported++
                        }
                    } else {
                        // Skip entire group
                        duplicates++
                    }
                }
            }
            
            onComplete(imported, duplicates)
        }
    }

    // Profile management
    fun createProfile(name: String, onSuccess: (Profile) -> Unit) {
        viewModelScope.launch {
            val newProfile = Profile(name = name, isDefault = false)
            val id = repository.insertProfile(newProfile)
            val profile = newProfile.copy(id = id)
            onSuccess(profile)
        }
    }

    fun deleteProfile(profile: Profile, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val wasCurrentProfile = _currentProfile.value?.id == profile.id
            
            // If deleting current profile, switch to another profile FIRST
            if (wasCurrentProfile) {
                val allProfiles = repository.getAllProfilesList()
                val remainingProfiles = allProfiles.filter { it.id != profile.id }
                
                if (remainingProfiles.isNotEmpty()) {
                    // Switch to first remaining profile
                    val newCurrentProfile = remainingProfiles.first()
                    _currentProfile.postValue(newCurrentProfile)
                    profilePrefs.currentProfileId = newCurrentProfile.id
                }
            }
            
            // Now delete the profile
            repository.deleteProfile(profile)
            
            onSuccess()
        }
    }

    fun renameProfile(profile: Profile, newName: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedProfile = profile.copy(name = newName)
            repository.updateProfile(updatedProfile)
            if (_currentProfile.value?.id == profile.id) {
                _currentProfile.postValue(updatedProfile)
            }
            onSuccess()
        }
    }

    fun setDefaultProfile(profile: Profile, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setDefaultProfile(profile)
            onSuccess()
        }
    }

    suspend fun getEntryCountForProfile(profileId: Long): Int {
        return repository.getEntryCountForProfile(profileId)
    }

    fun generateNoteGroupId(): String = repository.generateNoteGroupId()

    fun deleteVersionsByIds(ids: List<Long>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteNotesByIds(ids)
            onSuccess()
        }
    }
}
