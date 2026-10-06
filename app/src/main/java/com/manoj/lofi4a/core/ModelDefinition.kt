package com.manoj.lofi4a.core

enum class ModelType { TEXT, VISION, SPEECH }

data class ModelDefinition(
    val id: String,
    val type: ModelType,
    val displayName: String,
    val sizeLabel: String,
    val licenseName: String,
    val fileName: String,
    val downloadUrl: String
) {
    companion object {
        // Replace placeholder URLs with your real model hosting before release.
        val BUILTINS = listOf(
            ModelDefinition(
                id = "gemma3-1b-q4km",
                type = ModelType.TEXT,
                displayName = "Gemma 3 1B Instruct (Q4_K_M)",
                sizeLabel = "~800 MB",
                licenseName = "Apache 2.0",
                fileName = "gemma-3-1b-it-Q4_K_M.gguf",
                downloadUrl = "https://example.org/models/gemma-3-1b-it-Q4_K_M.gguf"
            ),
            ModelDefinition(
                id = "lfm2-vl-450m",
                type = ModelType.VISION,
                displayName = "LFM2-VL 450M",
                sizeLabel = "~350 MB",
                licenseName = "Apache 2.0",
                fileName = "lfm2-vl-450m.gguf",
                downloadUrl = "https://example.org/models/lfm2-vl-450m.gguf"
            ),
            ModelDefinition(
                id = "whisper-base",
                type = ModelType.SPEECH,
                displayName = "Whisper Base",
                sizeLabel = "~145 MB",
                licenseName = "MIT",
                fileName = "ggml-base.bin",
                downloadUrl = "https://example.org/models/ggml-base.bin"
            )
        )
    }
}
