package com.diary.app

import android.app.Application
import com.diary.app.utils.ThemeHelper

class DailygraphApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
    }
}
