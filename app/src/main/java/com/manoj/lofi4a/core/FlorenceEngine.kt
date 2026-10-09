package com.manoj.lofi4a.core

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Florence-2 base (ONNX, MIT). Step 1: download the model files and report their input/output names. */
object FlorenceEngine {
    private const val BASE = "https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/onnx/"
    private val SESSIONS = listOf("vision_encoder", "embed_tokens", "encoder_model", "decoder_model_merged")

    private fun dir(context: Context): File = File(context.filesDir, "florence").apply { mkdirs() }

    fun ensureFiles(context: Context) {
        for (name in SESSIONS) {
            val dest = File(dir(context), "$name.onnx")
            if (!dest.exists() || dest.length() == 0L) download(BASE + "$name.onnx", dest)
        }
    }

    private fun download(url: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for ${dest.name}")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(dest)) throw IOException("Could not save ${dest.name}")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Opens each Florence session and returns the real input/output names and shapes. */
    fun inspect(context: Context): String {
        ensureFiles(context)
        val env = OrtEnvironment.getEnvironment()
        val sb = StringBuilder("Florence-2 ONNX report:")
        for (name in SESSIONS) {
            val session: OrtSession = env.createSession(File(dir(context), "$name.onnx").absolutePath, OrtSession.SessionOptions())
            sb.append("\n\n[").append(name).append("]")
            session.inputInfo.forEach { (k, v) ->
                sb.append("\n in  ").append(k).append(" ").append((v.info as? TensorInfo)?.shape?.toList())
            }
            session.outputInfo.forEach { (k, v) ->
                sb.append("\n out ").append(k).append(" ").append((v.info as? TensorInfo)?.shape?.toList())
            }
            session.close()
        }
        return sb.toString()
    }
}
