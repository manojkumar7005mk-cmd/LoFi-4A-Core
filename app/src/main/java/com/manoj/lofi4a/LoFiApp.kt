package com.manoj.lofi4a

import android.app.Application
import com.manoj.lofi4a.core.ModelManager
import java.io.File

class LoFiApp : Application() {
    lateinit var modelManager: ModelManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Save any crash to a file, so the next launch can show it on screen
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                File(filesDir, CRASH_FILE).writeText(e.stackTraceToString())
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, e)
        }

        modelManager = ModelManager(this)
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
