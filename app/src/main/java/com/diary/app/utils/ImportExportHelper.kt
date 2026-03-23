package com.diary.app.utils

import android.content.Context
import android.net.Uri
import com.diary.app.data.Note
import com.diary.app.data.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object ImportExportHelper {

    fun exportAllNotesToJson(notes: List<Note>, profiles: List<Profile>): String {
        val rootObject = JSONObject()
        rootObject.put("exportVersion", 3)  // Version 3 for multi-profile export
        rootObject.put("exportDate", System.currentTimeMillis())
        
        // Export all profiles
        val profilesArray = JSONArray()
        for (profile in profiles) {
            val profileObj = JSONObject()
            profileObj.put("id", profile.id)
            profileObj.put("name", profile.name)
            profileObj.put("isDefault", profile.isDefault)
            profilesArray.put(profileObj)
        }
        rootObject.put("profiles", profilesArray)
        
        // Export all notes (including all versions from all profiles)
        val notesArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("profileId", note.profileId)
            obj.put("noteGroupId", note.noteGroupId)
            obj.put("version", note.version)
            obj.put("isLatest", note.isLatest)
            obj.put("date", note.date)
            obj.put("time", note.time)
            obj.put("temperature", note.temperature)
            obj.put("location", note.location)
            obj.put("content", note.content)
            obj.put("timestamp", note.timestamp)
            obj.put("editedAt", note.editedAt)
            notesArray.put(obj)
        }
        rootObject.put("notes", notesArray)
        
        return rootObject.toString(2)
    }

    fun exportNotesToJson(notes: List<Note>, profileName: String): String {
        // Keep old format for backwards compatibility
        val rootObject = JSONObject()
        rootObject.put("profileName", profileName)
        rootObject.put("exportVersion", 2)
        rootObject.put("exportDate", System.currentTimeMillis())
        
        val notesArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject()
            obj.put("noteGroupId", note.noteGroupId)
            obj.put("version", note.version)
            obj.put("date", note.date)
            obj.put("time", note.time)
            obj.put("temperature", note.temperature)
            obj.put("location", note.location)
            obj.put("content", note.content)
            obj.put("timestamp", note.timestamp)
            obj.put("editedAt", note.editedAt)
            notesArray.put(obj)
        }
        rootObject.put("notes", notesArray)
        
        return rootObject.toString(2)
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(content)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.appendLine(line)
                    }
                }
            }
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseNotesFromJson(jsonString: String, profileId: Long): List<Note>? {
        return try {
            val rootObject = JSONObject(jsonString.trim())
            
            // Handle both v1 (array) and v2 (object with metadata) formats
            val notesArray = if (rootObject.has("notes")) {
                rootObject.getJSONArray("notes")
            } else {
                // Fallback for old format (direct array)
                JSONArray(jsonString.trim())
            }
            
            val notes = mutableListOf<Note>()
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                
                // Generate new noteGroupId if not present (old format)
                val noteGroupId = obj.optString("noteGroupId", "note_${System.currentTimeMillis()}_$i")
                
                val note = Note(
                    profileId = profileId,
                    noteGroupId = noteGroupId,
                    version = obj.optInt("version", 1),
                    isLatest = true, // Imported notes are latest
                    date = obj.optString("date", ""),
                    time = obj.optString("time", ""),
                    temperature = obj.optString("temperature", "N/A"),
                    location = obj.optString("location", "N/A"),
                    content = obj.optString("content", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    editedAt = obj.optLong("editedAt", obj.optLong("timestamp", System.currentTimeMillis()))
                )
                notes.add(note)
            }
            notes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class FullImportData(
        val profiles: List<ProfileData>,
        val notes: List<Note>
    )

    data class ProfileData(
        val id: Long,
        val name: String,
        val isDefault: Boolean
    )

    fun parseAllNotesFromJson(jsonString: String): FullImportData? {
        return try {
            val rootObject = JSONObject(jsonString.trim())
            val exportVersion = rootObject.optInt("exportVersion", 1)
            
            // Check if this is v3 format (multi-profile export)
            if (exportVersion == 3 && rootObject.has("profiles")) {
                // Parse profiles
                val profilesArray = rootObject.getJSONArray("profiles")
                val profiles = mutableListOf<ProfileData>()
                for (i in 0 until profilesArray.length()) {
                    val profileObj = profilesArray.getJSONObject(i)
                    profiles.add(ProfileData(
                        id = profileObj.getLong("id"),
                        name = profileObj.getString("name"),
                        isDefault = profileObj.getBoolean("isDefault")
                    ))
                }
                
                // Parse notes with original profile IDs preserved
                val notesArray = rootObject.getJSONArray("notes")
                val notes = mutableListOf<Note>()
                for (i in 0 until notesArray.length()) {
                    val obj = notesArray.getJSONObject(i)
                    val note = Note(
                        profileId = obj.getLong("profileId"),
                        noteGroupId = obj.getString("noteGroupId"),
                        version = obj.getInt("version"),
                        isLatest = obj.getBoolean("isLatest"),
                        date = obj.getString("date"),
                        time = obj.getString("time"),
                        temperature = obj.optString("temperature", "N/A"),
                        location = obj.optString("location", "N/A"),
                        content = obj.getString("content"),
                        timestamp = obj.getLong("timestamp"),
                        editedAt = obj.getLong("editedAt")
                    )
                    notes.add(note)
                }
                
                return FullImportData(profiles, notes)
            } else {
                // Old format - return null to use fallback
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class ImportResult(
        val total: Int,
        val imported: Int,
        val duplicates: Int
    )
}
