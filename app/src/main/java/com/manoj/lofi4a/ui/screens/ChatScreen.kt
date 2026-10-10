package com.manoj.lofi4a.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manoj.lofi4a.R
import com.manoj.lofi4a.core.WavRecorder
import com.manoj.lofi4a.ui.Routes
import com.manoj.lofi4a.ui.chat.ChatMessage
import com.manoj.lofi4a.ui.chat.ChatViewModel
import java.io.File

private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return src
    val m = Matrix()
    m.postRotate(degrees)
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun makeThumb(src: Bitmap): Bitmap {
    val scale = 200f / maxOf(src.width, src.height)
    return Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

/** Reads the rotation the camera stored in the photo, so sideways photos are turned upright. */
private fun exifDegrees(context: Context, uri: Uri): Float = try {
    context.contentResolver.openInputStream(uri)?.use { s ->
        when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
} catch (e: Exception) {
    0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onNavigate: (String) -> Unit, vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val generating by vm.generating.collectAsState()
    val offline by vm.offline.collectAsState()
    val textLoaded by vm.textModelLoaded.collectAsState()
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
            val f = File(context.cacheDir, "picked_image.jpg")
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (decoded != null) {
                    val upright = rotateBitmap(decoded, exifDegrees(context, uri))
                    f.outputStream().use { o -> upright.compress(Bitmap.CompressFormat.JPEG, 90, o) }
                    pendingThumb = makeThumb(upright)
                    pendingImage = f
                }
            } catch (e: Exception) {
            }
        }
    }

    // Turns the waiting photo 90 degrees (for photos that are still sideways).
    val rotatePending: () -> Unit = {
        val file = pendingImage
        if (file != null) {
            try {
                val b = BitmapFactory.decodeFile(file.absolutePath)
                if (b != null) {
                    val r = rotateBitmap(b, 90f)
                    file.outputStream().use { o -> r.compress(Bitmap.CompressFormat.JPEG, 90, o) }
                    pendingThumb = makeThumb(r)
                }
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1, 100000)
    }

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

    val statusLine = buildString {
        append(if (textLoaded) "StudyMate Distilled Qwen · Ready" else "StudyMate Distilled Qwen · Not loaded")
        if (offline) append(" · Offline")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.studymate_logo),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "StudyMate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                statusLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(messages) { index, msg ->
                    val thinking = generating &&
                        index == messages.lastIndex &&
                        !msg.isUser &&
                        msg.text.isBlank()
                    MessageRow(msg, thinking)
                }
            }

            if (messages.size <= 1 && !generating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { input = "Explain this lesson simply: " },
                        label = { Text("Explain a lesson") }
                    )
                    SuggestionChip(
                        onClick = { input = "Solve step by step: " },
                        label = { Text("Solve a maths sum") }
                    )
                    SuggestionChip(
                        onClick = { imagePicker.launch("image/*") },
                        label = { Text("Read a book page") }
                    )
                }
            }

            // ChatGPT-style input bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    pendingThumb?.let { thumb ->
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = "Attached photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Photo attached",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Sideways? Tap rotate.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = rotatePending) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rotate photo")
                            }
                            IconButton(onClick = {
                                pendingImage = null
                                pendingThumb = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove photo")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !generating
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add photo")
                        }

                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(if (pendingImage != null) "Ask about this photo" else "Ask StudyMate")
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendNow() })
                        )

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
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = "Voice",
                                tint = if (recording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SendStopButton(
                            generating = generating,
                            canSend = input.isNotBlank() || pendingImage != null,
                            onSend = { sendNow() },
                            onStop = { vm.stop() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendStopButton(
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val bg = when {
        generating -> MaterialTheme.colorScheme.onSurface
        canSend -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = generating || canSend) {
                if (generating) onStop() else onSend()
            },
        contentAlignment = Alignment.Center
    ) {
        if (generating) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage, thinking: Boolean) {
    if (msg.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                SelectionContainer {
                    Text(
                        msg.text,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Image(
                painter = painterResource(R.drawable.studymate_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    if (thinking) AnnotatedString("Thinking…") else renderMarkdown(msg.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (thinking) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Turns simple markdown (**bold**, bullets, # headings) into styled text. */
private fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = latexToText(text).split("\n")
    lines.forEachIndexed { index, raw ->
        var line = raw.replace(Regex("^\\s*[*-]\\s+"), "• ")
        val heading = line.trimStart().startsWith("#")
        if (heading) line = line.trimStart().trimStart('#').trim()

        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            if (heading || i % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
        if (index < lines.lastIndex) append("\n")
    }
}

/** Turns simple LaTeX into readable text: $$ and $ removed, common symbols converted. */
private fun latexToText(input: String): String {
    var t = input.replace("$$", "").replace("$", "")
    val symbols = mapOf(
        "\\infty" to "∞", "\\sum" to "Σ", "\\prod" to "Π", "\\int" to "∫",
        "\\zeta" to "ζ", "\\xi" to "ξ", "\\dot" to "", "\\pi" to "π", "\\alpha" to "α",
        "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ", "\\theta" to "θ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\sigma" to "σ", "\\omega" to "ω", "\\leq" to "≤", "\\geq" to "≥",
        "\\neq" to "≠", "\\pm" to "±", "\\times" to "×", "\\cdot" to "·", "\\ldots" to "…",
        "\\dots" to "…", "\\to" to "→", "\\rightarrow" to "→", "\\approx" to "≈"
    )
    for ((k, v) in symbols) t = t.replace(Regex(k + "(?![a-zA-Z])"), v)
    t = t.replace(Regex("\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}"), "($1)/($2)")
    t = t.replace(Regex("\\\\(sqrt)\\{([^{}]*)\\}"), "√($2)")
    t = t.replace(Regex("\\\\\\{|\\\\\\}"), "")
    t = t.replace(Regex("\\^\\{([^{}]*)\\}"), "^($1)")
    t = t.replace(Regex("_\\{([^{}]*)\\}"), "_($1)")
    t = t.replace(Regex("\\\\(text|mathrm|mathbf)\\{([^{}]*)\\}"), "$2")
    return t
}
