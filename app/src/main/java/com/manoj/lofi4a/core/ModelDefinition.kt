package com.manoj.lofi4a.core

enum class ModelType { TEXT, VISION, SPEECH }

data class ModelDefinition(
    val id: String,
    val type: ModelType,
    val displayName: String,
    val role: String,
    val sizeLabel: String,
    val licenseName: String,
    val fileName: String,
    val downloadUrl: String,
    val mmprojFileName: String? = null,
    val mmprojUrl: String? = null,
    // more files that belong to this model: (path inside the models folder, download url)
    val extraFiles: List<Pair<String, String>> = emptyList()
) {
    companion object {
        val BUILTINS = listOf(
            ModelDefinition(
                id = "qwen3-1.7b-q4km",
                type = ModelType.TEXT,
                displayName = "Qwen3 1.7B",
                role = "Teacher · answers and explains",
                sizeLabel = "~1.1 GB",
                licenseName = "Apache 2.0",
                fileName = "Qwen3-1.7B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
            ),
            ModelDefinition(
                id = "florence-2-base",
                type = ModelType.VISION,
                displayName = "Florence-2 Base",
                role = "Image reader · describes photos and reads text",
                sizeLabel = "~1.1 GB",
                licenseName = "MIT",
                fileName = FlorenceEngine.FILES.first().first,
                downloadUrl = FlorenceEngine.FILES.first().second,
                extraFiles = FlorenceEngine.FILES.drop(1)
            ),
            ModelDefinition(
                id = "whisper-base",
                type = ModelType.SPEECH,
                displayName = "Whisper Base",
                role = "Voice · speech to text",
                sizeLabel = "~145 MB",
                licenseName = "MIT",
                fileName = "ggml-base.bin",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
            )
        )
    }
}
