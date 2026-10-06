package com.manoj.lofi4a.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manoj.lofi4a.LoFiApp
import com.manoj.lofi4a.core.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val modelManager = (app as LoFiApp).modelManager

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _textModelLoaded = MutableStateFlow(false)
    val textModelLoaded: StateFlow<Boolean> = _textModelLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            modelManager.status.collect { s ->
                _textModelLoaded.value = s.isLoaded(ModelType.TEXT)
            }
        }
        viewModelScope.launch {
            modelManager.offline.collect { _offline.value = it }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _messages.value = _messages.value + ChatMessage(text, isUser)
    }

    fun send(prompt: String) {
        if (prompt.isBlank()) return
        addMessage(prompt, isUser = true)
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                addMessage(modelManager.generateText(prompt), isUser = false)
            } catch (e: Exception) {
                addMessage("Error: ${e.message}", isUser = false)
            } finally {
                _generating.value = false
            }
        }
    }

    fun describeImage(imagePath: String) {
        addMessage("🖼️ Image attached", isUser = true)
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                addMessage(
                    modelManager.describeImage(imagePath, "Describe this image."),
                    isUser = false
                )
            } catch (e: Exception) {
                addMessage("Error: ${e.message}", isUser = false)
            } finally {
                _generating.value = false
            }
        }
    }

    fun transcribe(wavPath: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val text = modelManager.transcribe(wavPath)
                withContext(Dispatchers.Main) { onResult(text) }
            } catch (e: Exception) {
                addMessage("Error: ${e.message}", isUser = false)
            }
        }
    }
}
