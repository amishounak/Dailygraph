package com.diary.app.utils

import android.content.Context
import android.content.SharedPreferences

class ProfilePreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "profile_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_CURRENT_PROFILE_ID = "current_profile_id"
        private const val DEFAULT_PROFILE_ID = 1L // Default profile ID
    }

    var currentProfileId: Long
        get() = prefs.getLong(KEY_CURRENT_PROFILE_ID, DEFAULT_PROFILE_ID)
        set(value) = prefs.edit().putLong(KEY_CURRENT_PROFILE_ID, value).apply()

    fun setDefaultProfile() {
        currentProfileId = DEFAULT_PROFILE_ID
    }
}
