package com.manoj.lofi4a.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
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
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // A picked photo waits here until the student presses send.
    var pendingImage by remember { mutableStateOf<File?>(null) }
    var pendingThumb by remember { mutableStateOf<Bitmap?>(null) }

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
            // Shrink big phone photos to max ~1536px and save as a clean JPEG.
            val f = File(context.cacheDir, "picked_image.jpg")
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp != null) {
                    f.outputStream().use { o ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, o)
                    }
                    // small preview for the attachment strip
                    val scale = 160f / maxOf(bmp.width, bmp.height)
                    pendingThumb = Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    pendingImage = File(f.absolutePath)
                }
            } catch (e: Exception) {
            }
        }
    }

    // Keep the screen following the newest words while the answer streams in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1, 100000)
    }

    // Sends the text, and the waiting photo together with it (if there is one).
    val sendNow: () -> Unit = {
        val img = pendingImage
        val prompt = input.trim()
        if (!generating && (prompt.isNotEmpty() || img != null)) {
            input = ""
            if (img != null) {
                pendingImage = null
                pendingThumb = null
                vm.describeImage(img.absolutePath, prompt)
            } else {
                vm.send(prompt)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🎓 StudyMate AI", fontWeight = FontWeight.Bold)
                        Text(
                            "your offline study buddy",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (offline) {
                        Text(
                            "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = { vm.newChat() }, enabled = !generating) {
                        Icon(Icons.Default.Refresh, contentDescription = "New chat")
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
        // imePadding() lifts the whole column (and the message box) above the keyboard.
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }
            }

            if (messages.size <= 1 && pendingImage == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { input = "Explain this lesson in simple words: " },
                        label = { Text("📘 Explain a lesson") }
                    )
                    AssistChip(
                        onClick = { input = "Solve this sum step by step: " },
                        label = { Text("➗ Solve a sum") }
                    )
                    AssistChip(
                        onClick = { imagePicker.launch("image/*") },
                        label = { Text("📷 Read a book page") }
                    )
                }
            }

            // Bottom bar: attached-photo preview + message box
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    pendingThumb?.let { thumb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = "Attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Image attached. Type your question, then press send.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                pendingImage = null
                                pendingThumb = null
                            }) {
                                Text("✕", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !generating
                        ) {
                            Text("🖼️", style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(
                            onClick = {
                                if (recording) {
                                    recorder?.stop()
                                    recording = false
                                    vm.transcribe(audioFile.absolutePath) { input = it }
                                } else if (ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startRecording()
                                } else {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !generating
                        ) {
                            Text(if (recording) "⏹️" else "🎤", style = MaterialTheme.typography.titleLarge)
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (pendingImage != null) "Ask about this image…"
                                    else "Ask StudyMate anything…"
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 5,
                            enabled = !generating,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendNow() })
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { sendNow() },
                            enabled = !generating && (input.isNotBlank() || pendingImage != null),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            if (generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Turns the simple markdown the model writes (**bold**, * bullets, # headings) into styled text. */
private fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { index, raw ->
        var line = raw.replace(Regex("^\\s*[*-]\\s+"), "• ")
        val heading = line.trimStart().startsWith("#")
        if (heading) line = line.trimStart('#', ' ')
        var bold = heading
        line.split("**").forEachIndexed { i, part ->
            if (i > 0) bold = !bold
            withStyle(if (bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()) {
                append(part)
            }
        }
        if (index < lines.lastIndex) append("\n")
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val content = remember(msg.text) {
        renderMarkdown(if (msg.text.isBlank()) "…" else msg.text)
    }
    val isUser = msg.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("🎓")
            }
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                Text(
                    content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
