package com.facemorphapp

import android.app.Application
import com.facemorphapp.util.LocalCrashLogger
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FaceMorphApplication : Application() {

    @Inject lateinit var crashLogger: LocalCrashLogger

    override fun onCreate() {
        super.onCreate()
        crashLogger.install()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // No remote logging, no analytics, no Firebase — ever.
    }
}
