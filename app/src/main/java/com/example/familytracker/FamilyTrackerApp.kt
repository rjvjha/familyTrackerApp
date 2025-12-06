/*
* Lowe's Companies Inc., Android Application
* Copyright (C) 2023 Lowe's Companies Inc.
*
* The Lowe's Application is the private property of
* Lowe's Companies Inc. Any distribution of this software
* is unlawful and prohibited.
*/
package com.example.familytracker

import android.app.Application
import com.example.familytracker.location.TdLibManager

class FamilyTrackerApp: Application() {
    override fun onCreate() {
        super.onCreate()
        TdLibManager.initialize(applicationContext)
    }
}