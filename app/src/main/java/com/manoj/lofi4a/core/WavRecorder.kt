package com.manoj.lofi4a.core

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records 16 kHz mono 16-bit WAV (the format Whisper expects). */
class WavRecorder(private val outFile: File) {
    private val sampleRate = 16000

    @Volatile
    private var recording = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        recording = true
        rec.startRecording()
        thread = Thread {
            val pcm = ByteArrayOutputStream()
            val buf = ByteArray(minBuf)
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) pcm.write(buf, 0, n)
            }
            rec.stop()
            rec.release()
            writeWav(pcm.toByteArray())
        }.also { it.start() }
    }

    fun stop() {
        recording = false
        thread?.join()
    }

    private fun writeWav(pcm: ByteArray) {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt(36 + pcm.size)
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1)
        h.putShort(1)
        h.putInt(sampleRate)
        h.putInt(sampleRate * 2)
        h.putShort(2)
        h.putShort(16)
        h.put("data".toByteArray())
        h.putInt(pcm.size)
        outFile.outputStream().use {
            it.write(h.array())
            it.write(pcm)
        }
    }
}
