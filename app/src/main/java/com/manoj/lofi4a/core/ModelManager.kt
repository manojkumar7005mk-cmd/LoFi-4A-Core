    package com.manoj.lofi4a.core

    import android.content.Context
    import android.net.ConnectivityManager
    import android.net.NetworkCapabilities
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.*
    import java.io.File
import java.net.URL

    /**
     * Owns model download / load / unload lifecycle.
     *
     * Rules enforced here:
     *  - Inference always runs on [Dispatchers.Default], never on main.
     *  - At most ONE model of each type is loaded; loading unloads the previous one
     *    and its native memory is freed via NativeBridge.unload*().
     *  - Different TYPES may stay loaded, but [freeUnused] is called on memory pressure.
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

        fun isDownloaded(def: ModelDefinition): Boolean = modelFile(def).exists()

        private fun refreshStatuses() {
            _status.value = ModelStatus(
                text = stateFor(ModelType.TEXT),
                vision = stateFor(ModelType.VISION),
                speech = stateFor(ModelType.SPEECH)
            )
        }

        private fun stateFor(type: ModelType): ModelState = when (type) {
            ModelType.TEXT -> if (textCtx != 0L) ModelState.LOADED
                else if (isDownloaded(ModelDefinition.BUILTINS.first { it.type == type }))
                    ModelState.DOWNLOADED else ModelState.NOT_DOWNLOADED
            ModelType.VISION -> if (visionCtx != 0L) ModelState.LOADED
                else if (isDownloaded(ModelDefinition.BUILTINS.first { it.type == type }))
                    ModelState.DOWNLOADED else ModelState.NOT_DOWNLOADED
            ModelType.SPEECH -> if (speechCtx != 0L) ModelState.LOADED
                else if (isDownloaded(ModelDefinition.BUILTINS.first { it.type == type }))
                    ModelState.DOWNLOADED else ModelState.NOT_DOWNLOADED
        }

        // ---------------- Downloads (background) ----------------

        suspend fun download(def: ModelDefinition) {
            if (isDownloaded(def)) return
            _status.value = setDownloading(def.type, true)
            try {
                withContext(Dispatchers.IO) {
                    val tmp = File(modelsDir, def.fileName + ".part")
                    URL(def.downloadUrl).openStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var total = 0L
                            // content length may be unknown (-1) — report indeterminate as 0f..1f cycling
                            val length = runCatching {
                                URL(def.downloadUrl).openConnection().contentLengthLong
                            }.getOrDefault(-1L)
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                total += read
                                val p = if (length > 0) total.toFloat() / length else -1f
                                _downloadProgress.value = _downloadProgress.value +
                                    (def.id to (if (p < 0) 0.5f else p))
                                if (p >= 0 && p >= 1f) break
                            }
                        }
                    }
                    tmp.renameTo(modelFile(def))
                }
            } finally {
                _downloadProgress.value = _downloadProgress.value + (def.id to 1f)
                refreshStatuses()
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

        // ---------------- Load / unload (one per type, native cleanup) ----------------

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
                            // mmproj ships alongside the vision model
                            val mmproj = File(modelsDir, "mmproj-" + def.fileName)
                            visionCtx = NativeBridge.loadVisionModel(
                                modelFile(def).absolutePath, mmproj.absolutePath
                            )
                        }
                        ModelType.SPEECH -> {
                            unloadLocked(ModelType.SPEECH)
                            speechCtx = NativeBridge.loadSpeechModel(modelFile(def).absolutePath)
                        }
                    }
                    refreshStatuses()
                    Result.success(Unit)
                } catch (e: Exception) {
                    refreshStatuses()
                    Result.failure(e)
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

        /** Memory-pressure hook: free everything not currently generating. */
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

        suspend fun generateText(prompt: String): String = withContext(Dispatchers.Default) {
            val ctx = textCtx
            check(ctx != 0L) { "Text model is not loaded" }
            NativeBridge.generate(ctx, prompt, maxTokens = 256)
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
