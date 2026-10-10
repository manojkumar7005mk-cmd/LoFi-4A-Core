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

private const val WELCOME =
    "Hi, I'm StudyMate. I can explain lessons, solve maths step by step, and read pages from your book. " +
        "What would you like to work on?"

private const val READING = "Reading the image…"

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val modelManager = (app as LoFiApp).modelManager

    private val _messages = MutableStateFlow(listOf(ChatMessage(WELCOME, false)))
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
        _messages.update { it + ChatMessage(text, isUser) }
    }

    /** Appends text to the last (assistant) message while streaming. */
    private fun appendToLast(piece: String) {
        _messages.update { list ->
            if (list.isEmpty()) list
            else list.dropLast(1) + list.last().copy(text = list.last().text + piece)
        }
    }

    /** Shows an error in the current assistant bubble, keeping any text already streamed. */
    private fun failLast(msg: String) {
        _messages.update { list ->
            val last = list.lastOrNull()
            if (last == null || last.isUser) list + ChatMessage("Something went wrong: $msg", false)
            else {
                val t = if (last.text.isBlank() || last.text == READING) "Something went wrong: $msg"
                else last.text + "\n\nSomething went wrong: $msg"
                list.dropLast(1) + last.copy(text = t)
            }
        }
    }

    /** If the assistant bubble is still empty (the user pressed Stop early), mark it as stopped. */
    private fun markStoppedIfEmpty() {
        _messages.update { list ->
            val last = list.lastOrNull()
            if (last != null && !last.isUser && (last.text.isBlank() || last.text == READING)) {
                list.dropLast(1) + last.copy(text = "Stopped.")
            } else list
        }
    }

    /** Starts a fresh conversation. */
    fun newChat() {
        if (_generating.value) return
        modelManager.newChat()
        _messages.value = listOf(ChatMessage(WELCOME, false))
    }

    /** Stops whatever is running (reading the image or writing the answer). */
    fun stop() {
        modelManager.requestStop()
    }

    fun send(prompt: String) {
        if (prompt.isBlank() || _generating.value) return
        addMessage(prompt, isUser = true)
        addMessage("", isUser = false)
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                modelManager.generateTextStream(prompt) { appendToLast(it) }
            } catch (e: Exception) {
                failLast(e.message ?: "unknown error")
            } finally {
                markStoppedIfEmpty()
                _generating.value = false
            }
        }
    }

    /** LightOnOCR reads the image, then StudyMate answers using those notes. */
    fun describeImage(imagePath: String, question: String = "") {
        if (_generating.value) return
        addMessage(if (question.isBlank()) "Image attached" else question, isUser = true)
        addMessage(READING, isUser = false)
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                modelManager.analyzeImageStream(
                    imagePath, question,
                    onSeen = { seen ->
                        _messages.update { list ->
                            list.dropLast(1) +
                                ChatMessage("Image notes:\n$seen", false) +
                                ChatMessage("", false)
                        }
                    },
                    onToken = { appendToLast(it) }
                )
            } catch (e: Exception) {
                failLast(e.message ?: "unknown error")
            } finally {
                markStoppedIfEmpty()
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
                addMessage("Something went wrong: ${e.message}", isUser = false)
            }
        }
    }
}
