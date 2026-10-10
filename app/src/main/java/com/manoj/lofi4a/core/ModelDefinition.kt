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
                displayName = "StudyMate Distilled Qwen",
                role = "Teacher · answers and explains",
                sizeLabel = "~1.1 GB",
                licenseName = "Apache 2.0",
                fileName = "Qwen3-1.7B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
            ),
            ModelDefinition(
                id = "lighton-ocr-2-1b",
                type = ModelType.VISION,
                displayName = "LightOnOCR-2 1B",
                role = "Image reader · reads text, maths and notes from photos",
                sizeLabel = "~1.2 GB",
                licenseName = "Apache 2.0",
                fileName = "LightOnOCR-2-1B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/noctrex/LightOnOCR-2-1B-GGUF/resolve/main/LightOnOCR-2-1B-Q4_K_M.gguf",
                mmprojFileName = "LightOnOCR-2-1B-mmproj-F16.gguf",
                mmprojUrl = "https://huggingface.co/noctrex/LightOnOCR-2-1B-GGUF/resolve/main/mmproj-F16.gguf"
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
