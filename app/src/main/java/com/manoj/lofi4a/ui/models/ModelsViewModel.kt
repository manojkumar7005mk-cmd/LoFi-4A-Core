package com.manoj.lofi4a.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.lofi4a.LoFiApp
import com.manoj.lofi4a.core.ModelDefinition
import com.manoj.lofi4a.core.ModelType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ModelsViewModel(app: Application) : AndroidViewModel(app) {
    private val modelManager = (app as LoFiApp).modelManager

    val models: StateFlow<List<ModelDefinition>> =
        MutableStateFlow(ModelDefinition.BUILTINS).asStateFlow()

    val status = modelManager.status
    val downloadProgress = modelManager.downloadProgress

    fun download(def: ModelDefinition) {
        viewModelScope.launch { modelManager.download(def) }
    }

    fun load(def: ModelDefinition) {
        viewModelScope.launch { modelManager.load(def) }
    }

    fun unload(type: ModelType) {
        viewModelScope.launch { modelManager.unload(type) }
    }
}
