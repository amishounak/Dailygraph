package com.diary.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId"), Index("noteGroupId")]
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val profileId: Long,        // which profile this note belongs to
    val noteGroupId: String,    // groups all versions of the same note
    val version: Int = 1,       // version number (1 = original, 2+ = edits)
    val isLatest: Boolean = true, // only latest version shown in main list
    val date: String,           // e.g. "Monday, February 17, 2026"
    val time: String,           // e.g. "2:30 PM"
    val temperature: String,    // e.g. "22°C" or "N/A"
    val location: String,       // e.g. "New York, NY" or "N/A"
    val content: String,        // user's note text
    val timestamp: Long = System.currentTimeMillis(),  // for sorting
    val editedAt: Long = System.currentTimeMillis()    // when this version was created
)
