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
                id = "qwen3-1.7b-q4km",
                type = ModelType.TEXT,
                displayName = "StudyMate Main",
                sizeLabel = "~1.1 GB",
                licenseName = "School project only",
                fileName = "Qwen3-1.7B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
            ),
            ModelDefinition(
                id = "lfm25-vl-450m",
                type = ModelType.VISION,
                displayName = "StudyMate VL",
                sizeLabel = "~350 MB",
                licenseName = "School project only",
                fileName = "LFM2.5-VL-450M-Q4_0.gguf",
                downloadUrl = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/LFM2.5-VL-450M-Q4_0.gguf",
                mmprojFileName = "mmproj-LFM2.5-VL-450m-Q8_0.gguf",
                mmprojUrl = "https://huggingface.co/LiquidAI/LFM2.5-VL-450M-GGUF/resolve/main/mmproj-LFM2.5-VL-450m-Q8_0.gguf"
            ),
            // Speech is kept here because the model manager looks it up, but it is hidden from the list (see ModelsViewModel).
            ModelDefinition(
                id = "whisper-base",
                type = ModelType.SPEECH,
                displayName = "Whisper Base",
                sizeLabel = "~145 MB",
                licenseName = "School project only",
                fileName = "ggml-base.bin",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
            )
        )
    }
}
