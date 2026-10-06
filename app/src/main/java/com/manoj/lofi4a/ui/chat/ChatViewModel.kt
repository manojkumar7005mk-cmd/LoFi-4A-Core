    package com.manoj.lofi4a.ui.chat

    import android.app.Application
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.manoj.lofi4a.LoFiApp
import com.manoj.lofi4a.core.ModelType
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.*
    import kotlinx.coroutines.launch

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

        fun send(prompt: String) {
            if (prompt.isBlank()) return
            _messages.value = _messages.value + ChatMessage(prompt, isUser = true)
            _generating.value = true
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val reply = modelManager.generateText(prompt)
                    _messages.value = _messages.value + ChatMessage(reply, isUser = false)
                } catch (e: Exception) {
                    _messages.value = _messages.value +
                        ChatMessage("Error: ${e.message}", isUser = false)
                } finally {
                    _generating.value = false
                }
            }
        }
    }
