package com.manoj.lofi4a

import android.app.Application
import com.manoj.lofi4a.core.ModelManager

class LoFiApp : Application() {
    lateinit var modelManager: ModelManager
        private set

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
    }
}
