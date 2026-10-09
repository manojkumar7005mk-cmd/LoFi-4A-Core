package com.manoj.lofi4a.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Florence-2 base (ONNX, MIT). Reads a photo and returns plain-text notes:
 * a detailed description plus any text it can read (OCR). Qwen then answers from those notes.
 */
class FlorenceEngine(private val dir: File) : AutoCloseable {

    companion object {
        private const val BASE = "https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/"

        /** (path inside the models folder, download url) */
        val FILES: List<Pair<String, String>> = listOf(
            "florence/vision_encoder.onnx" to BASE + "onnx/vision_encoder.onnx",
            "florence/embed_tokens.onnx" to BASE + "onnx/embed_tokens.onnx",
            "florence/encoder_model.onnx" to BASE + "onnx/encoder_model.onnx",
            "florence/decoder_model.onnx" to BASE + "onnx/decoder_model.onnx",
            "florence/tokenizer.json" to BASE + "tokenizer.json"
        )

        private const val IMG = 768
        private const val HIDDEN = 768

        private val PROMPTS = mapOf(
            "<MORE_DETAILED_CAPTION>" to "Describe with a paragraph what is shown in the image.",
            "<OCR>" to "What is the text in the image?"
        )
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private fun open(name: String): OrtSession {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(4)
        return env.createSession(File(dir, name).absolutePath, opts)
    }

    private val vision = open("vision_encoder.onnx")
    private val embed = open("embed_tokens.onnx")
    private val encoder = open("encoder_model.onnx")
    private val decoder = open("decoder_model.onnx")
    private val tok = FlorenceTokenizer(File(dir, "tokenizer.json"))

    init {
        require(decoder.inputNames.containsAll(setOf("encoder_attention_mask", "encoder_hidden_states", "inputs_embeds"))) {
            "Florence decoder inputs are different than expected: ${decoder.inputNames}"
        }
    }

    // ---------- small tensor helpers ----------

    private fun floatTensor(data: FloatArray, vararg shape: Long): OnnxTensor {
        val bb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        bb.asFloatBuffer().put(data)
        return OnnxTensor.createTensor(env, bb.asFloatBuffer(), shape)
    }

    private fun longTensor(data: LongArray, vararg shape: Long): OnnxTensor {
        val bb = ByteBuffer.allocateDirect(data.size * 8).order(ByteOrder.nativeOrder())
        bb.asLongBuffer().put(data)
        return OnnxTensor.createTensor(env, bb.asLongBuffer(), shape)
    }

    private fun OnnxTensor.toFloats(): FloatArray {
        val fb = this.floatBuffer
        val a = FloatArray(fb.remaining())
        fb.get(a)
        return a
    }

    // ---------- image -> features ----------

    private fun pixelValues(imagePath: String): FloatArray {
        val src = BitmapFactory.decodeFile(imagePath) ?: error("Could not read the image file.")
        val bmp = Bitmap.createScaledBitmap(src, IMG, IMG, true)
        val px = IntArray(IMG * IMG)
        bmp.getPixels(px, 0, IMG, 0, 0, IMG, IMG)
        val out = FloatArray(3 * IMG * IMG)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val plane = IMG * IMG
        for (i in px.indices) {
            val c = px[i]
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            out[i] = (r - mean[0]) / std[0]
            out[plane + i] = (g - mean[1]) / std[1]
            out[2 * plane + i] = (b - mean[2]) / std[2]
        }
        if (bmp !== src) bmp.recycle()
        src.recycle()
        return out
    }

    private fun encodeImage(imagePath: String): Pair<FloatArray, Int> {
        val pv = pixelValues(imagePath)
        floatTensor(pv, 1, 3, IMG.toLong(), IMG.toLong()).use { t ->
            vision.run(mapOf("pixel_values" to t)).use { r ->
                val out = r.get(0) as OnnxTensor
                val tokens = out.info.shape[1].toInt()
                return out.toFloats() to tokens
            }
        }
    }

    private fun embedIds(ids: LongArray): FloatArray =
        longTensor(ids, 1, ids.size.toLong()).use { t ->
            embed.run(mapOf("input_ids" to t)).use { r -> (r.get(0) as OnnxTensor).toFloats() }
        }

    // ---------- one Florence task (caption or OCR) ----------

    private fun runTask(
        task: String,
        feats: FloatArray,
        nImg: Int,
        maxNew: Int,
        stop: () -> Boolean
    ): String {
        val ids = tok.encode(PROMPTS.getValue(task))
        val textEmb = embedIds(ids)
        val encLen = nImg + ids.size
        val encIn = FloatArray(encLen * HIDDEN)
        System.arraycopy(feats, 0, encIn, 0, nImg * HIDDEN)
        System.arraycopy(textEmb, 0, encIn, nImg * HIDDEN, ids.size * HIDDEN)
        val mask = LongArray(encLen) { 1L }

        val encHidden: FloatArray =
            floatTensor(encIn, 1, encLen.toLong(), HIDDEN.toLong()).use { a ->
                longTensor(mask, 1, encLen.toLong()).use { m ->
                    encoder.run(mapOf("attention_mask" to m, "inputs_embeds" to a)).use { r ->
                        (r.get(0) as OnnxTensor).toFloats()
                    }
                }
            }

        val logitsName = decoder.outputNames.firstOrNull { it == "logits" } ?: decoder.outputNames.first()
        val decEmb = FloatArray((maxNew + 3) * HIDDEN)
        var len = 0
        val gen = ArrayList<Long>()

        fun append(id: Long) {
            val e = embedIds(longArrayOf(id))
            System.arraycopy(e, 0, decEmb, len * HIDDEN, HIDDEN)
            len++
            gen.add(id)
        }

        append(2L) // decoder start token
        for (step in 0 until maxNew) {
            if (stop()) break
            val next: Long = if (step == 0) {
                0L // Florence always starts with <s>
            } else {
                val logits = decoderLogits(logitsName, encHidden, encLen, mask, decEmb, len)
                pickNext(logits, gen)
            }
            if (next == 2L) break
            append(next)
        }
        return tok.decode(gen)
    }

    private fun decoderLogits(
        outName: String,
        encHidden: FloatArray,
        encLen: Int,
        mask: LongArray,
        decEmb: FloatArray,
        len: Int
    ): FloatArray =
        floatTensor(encHidden, 1, encLen.toLong(), HIDDEN.toLong()).use { h ->
            longTensor(mask, 1, encLen.toLong()).use { m ->
                floatTensor(decEmb.copyOf(len * HIDDEN), 1, len.toLong(), HIDDEN.toLong()).use { e ->
                    val inputs = mapOf(
                        "encoder_attention_mask" to m,
                        "encoder_hidden_states" to h,
                        "inputs_embeds" to e
                    )
                    decoder.run(inputs, setOf(outName)).use { r ->
                        val lt = r.get(0) as OnnxTensor
                        val vocab = lt.info.shape[2].toInt()
                        val fb = lt.floatBuffer
                        val out = FloatArray(vocab)
                        fb.position((len - 1) * vocab)
                        fb.get(out)
                        out
                    }
                }
            }
        }

    /** Greedy pick, but never repeat the same 3 tokens in a row twice. */
    private fun pickNext(logits: FloatArray, gen: List<Long>): Long {
        val banned = HashSet<Int>()
        if (gen.size >= 3) {
            val a = gen[gen.size - 2]
            val b = gen[gen.size - 1]
            for (i in 0 until gen.size - 2) {
                if (gen[i] == a && gen[i + 1] == b) banned.add(gen[i + 2].toInt())
            }
        }
        var best = 2
        var bv = -Float.MAX_VALUE
        for (i in logits.indices) {
            if (i == 1 || i == 3) continue // pad / unk
            if (i in banned) continue
            if (logits[i] > bv) { bv = logits[i]; best = i }
        }
        return best.toLong()
    }

    /** Reads the image and returns text notes for the teacher model. */
    fun analyze(imagePath: String, stop: () -> Boolean): String {
        val (feats, n) = encodeImage(imagePath)
        val caption = runTask("<MORE_DETAILED_CAPTION>", feats, n, 160, stop).trim()
        val ocr = if (stop()) "" else runTask("<OCR>", feats, n, 256, stop).trim()
        return buildString {
            append("Description: ").append(caption)
            if (ocr.isNotBlank()) append("\nText in image: ").append(ocr)
        }
    }

    override fun close() {
        runCatching { vision.close() }
        runCatching { embed.close() }
        runCatching { encoder.close() }
        runCatching { decoder.close() }
    }
}

/** Byte-level BPE tokenizer (BART/GPT-2 style), loaded from tokenizer.json. */
class FlorenceTokenizer(file: File) {
    private val vocab = HashMap<String, Int>()
    private val idToTok = HashMap<Int, String>()
    private val ranks = HashMap<String, Int>()
    private val byteToChar = arrayOfNulls<Char>(256)
    private val charToByte = HashMap<Char, Int>()
    private val cache = HashMap<String, List<String>>()
    private val pattern =
        Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")

    init {
        val root = JSONObject(file.readText())
        val model = root.getJSONObject("model")
        val v = model.getJSONObject("vocab")
        val keys = v.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = v.getInt(k)
            vocab[k] = id
            idToTok[id] = k
        }
        val merges: JSONArray = model.getJSONArray("merges")
        for (i in 0 until merges.length()) {
            val m = merges.get(i)
            val key = if (m is String) m else {
                val arr = m as JSONArray
                arr.getString(0) + " " + arr.getString(1)
            }
            ranks[key] = i
        }
        // GPT-2 bytes -> printable unicode characters
        val bs = ArrayList<Int>()
        for (b in '!'.code..'~'.code) bs.add(b)
        for (b in '¡'.code..'¬'.code) bs.add(b)
        for (b in '®'.code..'ÿ'.code) bs.add(b)
        val cs = ArrayList<Int>(bs)
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        for (i in bs.indices) {
            byteToChar[bs[i]] = cs[i].toChar()
            charToByte[cs[i].toChar()] = bs[i]
        }
    }

    private fun bpe(word: String): List<String> {
        cache[word]?.let { return it }
        val parts = word.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (i in 0 until parts.size - 1) {
                val r = ranks[parts[i] + " " + parts[i + 1]] ?: continue
                if (r < bestRank) { bestRank = r; bestIdx = i }
            }
            if (bestIdx < 0) break
            parts[bestIdx] = parts[bestIdx] + parts[bestIdx + 1]
            parts.removeAt(bestIdx + 1)
        }
        cache[word] = parts
        return parts
    }

    /** <s> + tokens + </s> */
    fun encode(text: String): LongArray {
        val out = ArrayList<Long>()
        out.add(0L)
        for (m in pattern.findAll(text)) {
            val sb = StringBuilder()
            for (b in m.value.toByteArray(Charsets.UTF_8)) sb.append(byteToChar[b.toInt() and 0xFF])
            for (piece in bpe(sb.toString())) out.add((vocab[piece] ?: 3).toLong())
        }
        out.add(2L)
        return out.toLongArray()
    }

    fun decode(ids: List<Long>): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id in 0L..3L) continue
            val t = idToTok[id.toInt()] ?: continue
            sb.append(t)
        }
        val bytes = ByteArrayOutputStream()
        for (c in sb) {
            val b = charToByte[c] ?: continue
            bytes.write(b)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8).trim()
    }
}
