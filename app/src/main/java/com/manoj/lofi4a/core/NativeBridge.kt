package com.manoj.lofi4a.core

/** Receives generated text piece by piece. Return false to stop generating. */
fun interface TokenCallback {
    fun onToken(piece: String): Boolean
}

/**
 * JNI bridge to the native llama.cpp / whisper.cpp runtime.
 *
 * This class is the ONLY place that talks to liblofi_jni.so.
 */
object NativeBridge {
    init {
        System.loadLibrary("lofi_jni")
    }

    // ---- Text (llama.cpp) ----
    external fun loadTextModel(path: String): Long
    external fun generate(textModelCtx: Long, prompt: String, maxTokens: Int): String
    external fun generateStream(textModelCtx: Long, prompt: String, maxTokens: Int, callback: TokenCallback): String
    external fun unloadTextModel(ctx: Long)

    // ---- Vision (llama.cpp mtmd) ----
    external fun loadVisionModel(modelPath: String, mmprojPath: String): Long
    external fun describeImage(visionCtx: Long, imagePath: String, prompt: String): String
    external fun unloadVisionModel(ctx: Long)

    // ---- Speech (whisper.cpp) ----
    external fun loadSpeechModel(path: String): Long
    external fun transcribe(speechCtx: Long, wavPath: String): String
    external fun unloadSpeechModel(ctx: Long)

    /** Force-free all native allocations (called from onTrimMemory too). */
    external fun freeAll()
}
