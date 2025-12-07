package com.example.familytracker

import android.app.Application
import com.example.familytracker.location.TdLibManager

class FamilyTrackerApp: Application() {
    override fun onCreate() {
        super.onCreate()
        TdLibManager.initialize(applicationContext)
    }
}