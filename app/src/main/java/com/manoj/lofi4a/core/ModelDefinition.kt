package com.manoj.lofi4a.core

enum class ModelType { TEXT, VISION, SPEECH }

data class ModelDefinition(
    val id: String,
    val type: ModelType,
    val displayName: String,
    val sizeLabel: String,
    val licenseName: String,
    val fileName: String,
    val downloadUrl: String,
    val mmprojFileName: String? = null,
    val mmprojUrl: String? = null
) {
    companion object {
        val BUILTINS = listOf(
            ModelDefinition(
                id = "gemma3-1b-q4km",
                type = ModelType.TEXT,
                displayName = "Gemma 3 1B Instruct (Q4_K_M)",
                sizeLabel = "~806 MB",
                licenseName = "Gemma Terms of Use",
                fileName = "gemma-3-1b-it-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"
            ),
            ModelDefinition(
                id = "lfm25-vl-450m",
                type = ModelType.VISION,
                displayName = "LFM2.5-VL 450M (Q4_0)",
                sizeLabel = "~350 MB",
                licenseName = "LFM Open License",
                fileName = "LFM2.5-VL-450M-Q4_0.gguf",
                downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/LFM2.5-VL-450M-Q4_0.gguf",
                mmprojFileName = "mmproj-LFM2.5-VL-450m-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/mmproj-LFM2.5-VL-450m-Q8_0.gguf"
            ),
            ModelDefinition(
                id = "whisper-base",
                type = ModelType.SPEECH,
                displayName = "Whisper Base",
                sizeLabel = "~145 MB",
                licenseName = "MIT",
                fileName = "ggml-base.bin",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
            )
        )
    }
}
