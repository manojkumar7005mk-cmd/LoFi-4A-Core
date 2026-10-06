package com.manoj.lofi4a.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.lofi4a.core.WavRecorder
import com.manoj.lofi4a.ui.Routes
import com.manoj.lofi4a.ui.chat.ChatMessage
import com.manoj.lofi4a.ui.chat.ChatViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onNavigate: (String) -> Unit, vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val generating by vm.generating.collectAsState()
    val offline by vm.offline.collectAsState()
    val textModelLoaded by vm.textModelLoaded.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<WavRecorder?>(null) }
    val audioFile = remember { File(context.cacheDir, "mic.wav") }

    val startRecording: () -> Unit = {
        try {
            val r = WavRecorder(audioFile)
            r.start()
            recorder = r
            recording = true
        } catch (e: Exception) {
            recording = false
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val f = File(context.cacheDir, "picked_image")
            context.contentResolver.openInputStream(uri)?.use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            }
            vm.describeImage(f.absolutePath)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LoFi-4A Core") },
                actions = {
                    if (offline) {
                        Text(
                            "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { onNavigate(Routes.MODELS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Models")
                    }
                    IconButton(onClick = { onNavigate(Routes.ABOUT) }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!textModelLoaded) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "Text model not loaded. Open Models to download and load it.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !generating
                ) {
                    Text("🖼️", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = {
                    if (recording) {
                        recorder?.stop()
                        recorder = null
                        recording = false
                        vm.transcribe(audioFile.absolutePath) { text ->
                            input = (input + " " + text).trim()
                        }
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) startRecording()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text(
                        if (recording) "⏹️" else "🎤",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message LoFi-4A...") },
                    enabled = textModelLoaded && !generating
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val prompt = input
                        input = ""
                        vm.send(prompt)
                    },
                    enabled = textModelLoaded && !generating && input.isNotBlank()
                ) {
                    if (generating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (msg.isUser)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(msg.text, modifier = Modifier.padding(10.dp))
        }
    }
}
