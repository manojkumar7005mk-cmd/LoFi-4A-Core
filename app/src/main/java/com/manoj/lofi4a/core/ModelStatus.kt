package com.manoj.lofi4a.core

enum class ModelState { NOT_DOWNLOADED, DOWNLOADED, DOWNLOADING, LOADING, LOADED, ERROR }

data class ModelStatus(
    val text: ModelState = ModelState.NOT_DOWNLOADED,
    val vision: ModelState = ModelState.NOT_DOWNLOADED,
    val speech: ModelState = ModelState.NOT_DOWNLOADED
) {
    fun stateFor(type: ModelType): ModelState = when (type) {
        ModelType.TEXT -> text
        ModelType.VISION -> vision
        ModelType.SPEECH -> speech
    }

    fun isLoaded(type: ModelType): Boolean = stateFor(type) == ModelState.LOADED
}
