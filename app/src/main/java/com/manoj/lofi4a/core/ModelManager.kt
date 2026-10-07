package com.manoj.lofi4a.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Owns model download / load / unload lifecycle.
 * Inference always runs off the main thread.
 */
class ModelManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val _status = MutableStateFlow(ModelStatus())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    // Native context handles; 0 = unloaded
    private var textCtx: Long = 0
    private var visionCtx: Long = 0
    private var speechCtx: Long = 0

    private val loadLock = Any()

    init {
        refreshStatuses()
        monitorConnectivity()
    }

    fun modelFile(def: ModelDefinition): File = File(modelsDir, def.fileName)

    private fun mmprojFile(def: ModelDefinition): File? =
        def.mmprojFileName?.let { File(modelsDir, it) }

    fun isDownloaded(def: ModelDefinition): Boolean =
        modelFile(def).exists() && (mmprojFile(def)?.exists() ?: true)

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatuses() {
        _status.value = ModelStatus(
            text = stateFor(ModelType.TEXT),
            vision = stateFor(ModelType.VISION),
            speech = stateFor(ModelType.SPEECH)
        )
    }

    private fun stateFor(type: ModelType): ModelState {
        val handle = when (type) {
            ModelType.TEXT -> textCtx
            ModelType.VISION -> visionCtx
            ModelType.SPEECH -> speechCtx
        }
        if (handle != 0L) return ModelState.LOADED
        val def = ModelDefinition.BUILTINS.first { it.type == type }
        return if (isDownloaded(def)) ModelState.DOWNLOADED else ModelState.NOT_DOWNLOADED
    }

    // ---------------- Downloads ----------------

    suspend fun download(def: ModelDefinition) {
        if (isDownloaded(def)) return
        _status.value = setDownloading(def.type, true)
        _downloadProgress.value = _downloadProgress.value + (def.id to 0f)
        try {
            withContext(Dispatchers.IO) {
                val files = mutableListOf<Pair<String, File>>()
                files.add(def.downloadUrl to modelFile(def))
                val projUrl = def.mmprojUrl
                val projFile = mmprojFile(def)
                if (projUrl != null && projFile != null) files.add(projUrl to projFile)

                files.forEachIndexed { index, pair ->
                    val url = pair.first
                    val dest = pair.second
                    if (!dest.exists()) {
                        downloadFile(url, dest) { p ->
                            _downloadProgress.value = _downloadProgress.value +
                                (def.id to ((index + p) / files.size))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast("Download failed: ${e.message}")
        } finally {
            _downloadProgress.value = _downloadProgress.value - def.id
            refreshStatuses()
        }
    }

    private fun downloadFile(urlStr: String, dest: File, onProgress: (Float) -> Unit) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val length = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        total += read
                        if (length > 0) {
                            val pct = (total * 100 / length).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(total.toFloat() / length)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(dest)) throw IOException("Could not save file")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun setDownloading(type: ModelType, on: Boolean): ModelStatus {
        val s = if (on) ModelState.DOWNLOADING else ModelState.NOT_DOWNLOADED
        return when (type) {
            ModelType.TEXT -> _status.value.copy(text = s)
            ModelType.VISION -> _status.value.copy(vision = s)
            ModelType.SPEECH -> _status.value.copy(speech = s)
        }
    }

    // ---------------- Load / unload ----------------

    suspend fun load(def: ModelDefinition): Result<Unit> = withContext(Dispatchers.Default) {
        synchronized(loadLock) {
            try {
                when (def.type) {
                    ModelType.TEXT -> {
                        unloadLocked(ModelType.TEXT)
                        textCtx = NativeBridge.loadTextModel(modelFile(def).absolutePath)
                    }
                    ModelType.VISION -> {
                        unloadLocked(ModelType.VISION)
                        val proj = mmprojFile(def) ?: error("No projector file for ${def.displayName}")
                        visionCtx = NativeBridge.loadVisionModel(
                            modelFile(def).absolutePath, proj.absolutePath
                        )
                    }
                    ModelType.SPEECH -> {
                        unloadLocked(ModelType.SPEECH)
                        speechCtx = NativeBridge.loadSpeechModel(modelFile(def).absolutePath)
                    }
                }
                refreshStatuses()
                Result.success(Unit)
            } catch (t: Throwable) {
                refreshStatuses()
                toast("Load failed: ${t.message}")
                Result.failure(t)
            }
        }
    }

    suspend fun unload(type: ModelType) = withContext(Dispatchers.Default) {
        synchronized(loadLock) {
            unloadLocked(type)
            refreshStatuses()
        }
    }

    private fun unloadLocked(type: ModelType) {
        when (type) {
            ModelType.TEXT -> { if (textCtx != 0L) NativeBridge.unloadTextModel(textCtx); textCtx = 0 }
            ModelType.VISION -> { if (visionCtx != 0L) NativeBridge.unloadVisionModel(visionCtx); visionCtx = 0 }
            ModelType.SPEECH -> { if (speechCtx != 0L) NativeBridge.unloadSpeechModel(speechCtx); speechCtx = 0 }
        }
    }

    /** Memory-pressure hook: free everything. */
    fun freeAll() {
        scope.launch {
            synchronized(loadLock) {
                NativeBridge.freeAll()
                textCtx = 0; visionCtx = 0; speechCtx = 0
                refreshStatuses()
            }
        }
    }

    // ---------------- Inference (background threads) ----------------

    private val systemPrompt =
        "You are LoFi-4A Core, the main AI assistant in the LoFi model family. " +
            "You run fully offline on the user's phone. " +
            "If asked who made you, say you are part of the LoFi model family and do not name any company. " +
            "Answer clearly and helpfully."

    private fun gemmaPrompt(user: String) =
        "<start_of_turn>user\n$systemPrompt\n\n$user<end_of_turn>\n<start_of_turn>model\n"

    private suspend fun ensureLoaded(type: ModelType) {
        val loaded = when (type) {
            ModelType.TEXT -> textCtx
            ModelType.VISION -> visionCtx
            ModelType.SPEECH -> speechCtx
        } != 0L
        if (loaded) return
        val def = ModelDefinition.BUILTINS.first { it.type == type }
        if (!isDownloaded(def)) error("${def.displayName} is not downloaded. Open Models and download it.")
        load(def).getOrThrow()
    }

    /** Streams Gemma's answer: onToken is called with each piece as it is generated. */
    suspend fun generateTextStream(prompt: String, onToken: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            ensureLoaded(ModelType.TEXT)
            NativeBridge.generateStream(
                textCtx, gemmaPrompt(prompt), 512,
                TokenCallback { piece -> onToken(piece); true }
            )
        }

    /**
     * Pipeline: LFM2.5-VL looks at the image (onSeen gets its description),
     * then Gemma streams the final answer.
     */
    suspend fun analyzeImageStream(
        imagePath: String,
        question: String,
        onSeen: (String) -> Unit,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.Default) {
        ensureLoaded(ModelType.VISION)
        val seen = NativeBridge.describeImage(
            visionCtx, imagePath,
            "Describe this image in detail. Include any visible text, objects, people, colors and the setting."
        )
        unload(ModelType.VISION) // free RAM before loading Gemma
        if (seen.isBlank()) error("The vision model returned nothing.")
        onSeen(seen)
        ensureLoaded(ModelType.TEXT)
        val ask = if (question.isBlank()) "Explain what is in this image." else question
        NativeBridge.generateStream(
            textCtx,
            gemmaPrompt(
                "A vision model looked at an image the user shared and described it like this:\n\n" +
                    "$seen\n\nUsing only that description, respond to the user's request: $ask"
            ),
            512,
            TokenCallback { piece -> onToken(piece); true }
        )
    }

    suspend fun transcribe(wavPath: String): String = withContext(Dispatchers.Default) {
        ensureLoaded(ModelType.SPEECH)
        NativeBridge.transcribe(speechCtx, wavPath)
    }

    // ---------------- Connectivity ----------------

    private fun monitorConnectivity() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _offline.value = !isOnline(cm)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { _offline.value = false }
            override fun onLost(network: android.net.Network) { _offline.value = true }
        })
    }

    private fun isOnline(cm: ConnectivityManager): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
