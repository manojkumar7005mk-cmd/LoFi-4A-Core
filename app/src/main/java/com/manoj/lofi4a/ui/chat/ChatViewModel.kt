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
    "Hi! I'm StudyMate AI 👋 your friendly offline study buddy.\n\n" +
        "Ask me to explain a lesson, solve a sum step by step, or tap 🖼️ to share a page from your book."

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

    /** Appends text to the last (bot) message — used while streaming. */
    private fun appendToLast(piece: String) {
        _messages.update { list ->
            if (list.isEmpty()) list
            else list.dropLast(1) + list.last().copy(text = list.last().text + piece)
        }
    }

    /** Shows an error in the current bot bubble (keeps any text already streamed). */
    private fun failLast(msg: String) {
        _messages.update { list ->
            if (list.isEmpty() || list.last().isUser) list + ChatMessage("Error: $msg", false)
            else {
                val last = list.last()
                val t = if (last.text.isBlank()) "Error: $msg" else last.text + "\n\nError: $msg"
                list.dropLast(1) + last.copy(text = t)
            }
        }
    }

    /** Starts a fresh conversation (clears StudyMate's memory too). */
    fun newChat() {
        if (_generating.value) return
        modelManager.newChat()
        _messages.value = listOf(ChatMessage(WELCOME, false))
    }

    fun send(prompt: String) {
        if (prompt.isBlank() || _generating.value) return
        addMessage(prompt, isUser = true)
        addMessage("", isUser = false) // bubble that fills in as tokens arrive
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                modelManager.generateTextStream(prompt) { appendToLast(it) }
            } catch (e: Exception) {
                failLast(e.message ?: "unknown error")
            } finally {
                _generating.value = false
            }
        }
    }

    /** LFM2.5-VL looks at the image, then StudyMate (Gemma) explains and streams the answer. */
    fun describeImage(imagePath: String, question: String = "") {
        if (_generating.value) return
        addMessage(if (question.isBlank()) "🖼️ Image attached" else "🖼️ $question", isUser = true)
        addMessage("🔍 Looking at the image…", isUser = false)
        _generating.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                modelManager.analyzeImageStream(
                    imagePath, question,
                    onSeen = { seen ->
                        // replace the "Looking…" bubble with what LFM saw, then start the answer bubble
                        _messages.update { list ->
                            list.dropLast(1) +
                                ChatMessage("👁️ What I could see:\n$seen", false) +
                                ChatMessage("", false)
                        }
                    },
                    onToken = { appendToLast(it) }
                )
            } catch (e: Exception) {
                failLast(e.message ?: "unknown error")
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
