#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <functional>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "LoFi-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
constexpr int N_CTX = 4096;
constexpr int N_BATCH = 512;
constexpr int N_THREADS = 4;

struct TextCtx { llama_model* model; llama_context* lctx; };
struct VisionCtx { llama_model* model; llama_context* lctx; mtmd_context* mctx; };

using PieceFn = std::function<bool(const std::string&)>;

std::once_flag g_init;
std::string g_last_log;

void log_cb(enum ggml_log_level level, const char* text, void*) {
    if (!text) return;
    if (level >= GGML_LOG_LEVEL_WARN) {
        g_last_log += text;
        if (g_last_log.size() > 300) g_last_log = g_last_log.substr(g_last_log.size() - 300);
    }
    __android_log_print(ANDROID_LOG_INFO, "LoFi-llama", "%s", text);
}

void init_backend() {
    std::call_once(g_init, [] {
        llama_log_set(log_cb, nullptr);
        mtmd_helper_log_set(log_cb, nullptr);
        llama_backend_init();
    });
}

std::string to_std(JNIEnv* env, jstring s) {
    jclass sc = env->FindClass("java/lang/String");
    jmethodID gb = env->GetMethodID(sc, "getBytes", "(Ljava/lang/String;)[B");
    jstring enc = env->NewStringUTF("UTF-8");
    auto arr = (jbyteArray) env->CallObjectMethod(s, gb, enc);
    jsize n = env->GetArrayLength(arr);
    std::string r((size_t) n, '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, (jbyte*) &r[0]);
    env->DeleteLocalRef(arr); env->DeleteLocalRef(enc); env->DeleteLocalRef(sc);
    return r;
}

jstring to_jstring(JNIEnv* env, const std::string& s) {
    jclass sc = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(sc, "<init>", "([BLjava/lang/String;)V");
    jbyteArray arr = env->NewByteArray((jsize) s.size());
    if (!s.empty()) env->SetByteArrayRegion(arr, 0, (jsize) s.size(), (const jbyte*) s.data());
    jstring enc = env->NewStringUTF("UTF-8");
    auto r = (jstring) env->NewObject(sc, ctor, arr, enc);
    env->DeleteLocalRef(arr); env->DeleteLocalRef(enc); env->DeleteLocalRef(sc);
    return r;
}

void fail(JNIEnv* env, const std::string& msg) {
    LOGE("%s", msg.c_str());
    jclass c = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(c, msg.c_str());
}

// Number of leading bytes of s that form complete UTF-8 characters.
size_t utf8_complete_prefix(const std::string& s) {
    size_t n = s.size();
    size_t i = n;
    int back = 0;
    while (i > 0 && back < 4) {
        unsigned char c = (unsigned char) s[i - 1];
        if ((c & 0xC0) == 0x80) { i--; back++; continue; }
        size_t need = (c >= 0xF0) ? 4 : (c >= 0xE0) ? 3 : (c >= 0xC0) ? 2 : 1;
        size_t have = n - (i - 1);
        return have >= need ? n : i - 1;
    }
    return n;
}

llama_model* load_model(const std::string& path) {
    init_backend();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    return llama_model_load_from_file(path.c_str(), mp);
}

llama_context* new_context(llama_model* model) {
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = N_CTX;
    cp.n_batch = N_BATCH;
    cp.n_ubatch = N_BATCH;
    cp.n_threads = N_THREADS;
    cp.n_threads_batch = N_THREADS;
    return llama_init_from_model(model, cp);
}

std::vector<llama_token> tokenize(const llama_vocab* v, const std::string& t) {
    int n = -llama_tokenize(v, t.c_str(), (int32_t) t.size(), nullptr, 0, true, true);
    std::vector<llama_token> r(n > 0 ? n : 0);
    if (n <= 0 || llama_tokenize(v, t.c_str(), (int32_t) t.size(), r.data(), (int32_t) r.size(), true, true) < 0)
        r.clear();
    return r;
}

bool decode_all(llama_context* lctx, std::vector<llama_token>& toks) {
    for (size_t i = 0; i < toks.size(); i += N_BATCH) {
        int n = (int) std::min<size_t>(N_BATCH, toks.size() - i);
        if (llama_decode(lctx, llama_batch_get_one(toks.data() + i, n)) != 0) return false;
    }
    return true;
}

// Samples after the prompt has already been decoded. Calls on_piece (if set)
// with each chunk of complete UTF-8 text as soon as it is produced.
// A hand-made repeat penalty and a repetition guard stop small models from looping.
std::string sample_loop(llama_context* lctx, const llama_vocab* vocab, int maxTokens,
                        const PieceFn& on_piece, float temp) {
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
llama_sampler_chain_add(smpl, llama_sampler_init_top_k(20));
llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    const int n_vocab = llama_vocab_n_tokens(vocab);
    const float repeat_penalty = 1.15f;
    std::vector<llama_token> recent;   // the last 64 tokens
    std::string out, pending;
    bool keep_going = true;
    for (int i = 0; i < maxTokens && keep_going; i++) {
        // push down tokens that were just used, so the model doesn't repeat itself
        float* logits = llama_get_logits_ith(lctx, -1);
        if (logits && !recent.empty()) {
            std::vector<llama_token> uniq(recent);
            std::sort(uniq.begin(), uniq.end());
            uniq.erase(std::unique(uniq.begin(), uniq.end()), uniq.end());
            for (llama_token t : uniq) {
                if (t >= 0 && t < n_vocab) {
                    logits[t] = logits[t] > 0 ? logits[t] / repeat_penalty : logits[t] * repeat_penalty;
                }
            }
        }
        llama_token tok = llama_sampler_sample(smpl, lctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;
        recent.push_back(tok);
        if (recent.size() > 64) recent.erase(recent.begin());

        char buf[256];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, false);
        if (n > 0) {
            out.append(buf, (size_t) n);
            pending.append(buf, (size_t) n);
            size_t k = utf8_complete_prefix(pending);
            if (k > 0 && on_piece) {
                std::string chunk = pending.substr(0, k);
                pending.erase(0, k);
                if (!on_piece(chunk)) keep_going = false;
            }
        }
        // stop if the last 60 characters just repeated the 60 before them
        if (out.size() > 160 && (i % 8 == 0)) {
            size_t L = out.size();
            if (out.compare(L - 60, 60, out, L - 120, 60) == 0) break;
        }
        if (llama_decode(lctx, llama_batch_get_one(&tok, 1)) != 0) break;
    }
    if (!pending.empty() && on_piece && keep_going) on_piece(pending);
    llama_sampler_free(smpl);
    return out;
}

// Builds a callback that sends each piece to a Kotlin TokenCallback.onToken(String): Boolean
PieceFn make_emitter(JNIEnv* env, jobject cb) {
    if (!cb) return nullptr;
    jclass cls = env->GetObjectClass(cb);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
    env->DeleteLocalRef(cls);
    if (!mid) { env->ExceptionClear(); return nullptr; }
    return [env, cb, mid](const std::string& s) -> bool {
        jstring js = to_jstring(env, s);
        jboolean keep = env->CallBooleanMethod(cb, mid, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        return keep == JNI_TRUE;
    };
}

jstring run_generate(JNIEnv* env, jlong ptr, jstring prompt, jint maxTokens, jobject cb) {
    auto* c = reinterpret_cast<TextCtx*>(ptr);
    if (!c) { fail(env, "Text model is not loaded."); return nullptr; }
    const llama_vocab* vocab = llama_model_get_vocab(c->model);
    std::vector<llama_token> toks = tokenize(vocab, to_std(env, prompt));
    if (toks.empty()) { fail(env, "Could not tokenize prompt."); return nullptr; }
    if ((int) toks.size() + maxTokens > N_CTX) { fail(env, "Prompt is too long."); return nullptr; }
    llama_memory_clear(llama_get_memory(c->lctx), true);
    if (!decode_all(c->lctx, toks)) { fail(env, "Text model failed to read the prompt."); return nullptr; }
    PieceFn emit = make_emitter(env, cb);
    return to_jstring(env, sample_loop(c->lctx, vocab, maxTokens, emit, 0.6f));
}
} // namespace

extern "C" {

// ---------------- Text (llama.cpp) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadTextModel(JNIEnv* env, jobject, jstring path) {
    std::string p = to_std(env, path);
    LOGI("loadTextModel: %s", p.c_str());
    llama_model* model = load_model(p);
    if (!model) { fail(env, "Could not load text model (file missing or unsupported)."); return 0; }
    llama_context* lctx = new_context(model);
    if (!lctx) { llama_model_free(model); fail(env, "Could not create text context (out of memory?)."); return 0; }
    return reinterpret_cast<jlong>(new TextCtx{model, lctx});
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_generate(JNIEnv* env, jobject, jlong ptr, jstring prompt, jint maxTokens) {
    return run_generate(env, ptr, prompt, maxTokens, nullptr);
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_generateStream(JNIEnv* env, jobject, jlong ptr, jstring prompt, jint maxTokens, jobject callback) {
    return run_generate(env, ptr, prompt, maxTokens, callback);
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadTextModel(JNIEnv*, jobject, jlong ptr) {
    auto* c = reinterpret_cast<TextCtx*>(ptr);
    if (!c) return;
    llama_free(c->lctx);
    llama_model_free(c->model);
    delete c;
}

// ---------------- Vision (LFM2.5-VL via mtmd) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadVisionModel(JNIEnv* env, jobject, jstring modelPath, jstring mmprojPath) {
    std::string m = to_std(env, modelPath), mm = to_std(env, mmprojPath);
    LOGI("loadVisionModel: %s + %s", m.c_str(), mm.c_str());
    llama_model* model = load_model(m);
    if (!model) { fail(env, "Could not load vision model."); return 0; }
    llama_context* lctx = new_context(model);
    if (!lctx) { llama_model_free(model); fail(env, "Could not create vision context."); return 0; }
    mtmd_context_params mp = mtmd_context_params_default();
    mp.use_gpu = false;
    mp.n_threads = N_THREADS;
    mtmd_context* mctx = mtmd_init_from_file(mm.c_str(), model, mp);
    if (!mctx) {
        llama_free(lctx); llama_model_free(model);
        fail(env, "Could not load the vision projector (mmproj) file.");
        return 0;
    }
    return reinterpret_cast<jlong>(new VisionCtx{model, lctx, mctx});
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_describeImage(JNIEnv* env, jobject, jlong ptr, jstring imagePath, jstring prompt) {
    auto* c = reinterpret_cast<VisionCtx*>(ptr);
    if (!c) { fail(env, "Vision model is not loaded."); return nullptr; }
    std::string img = to_std(env, imagePath), pr = to_std(env, prompt);

    mtmd_helper_bitmap_wrapper wrap = mtmd_helper_bitmap_init_from_file(c->mctx, img.c_str(), false);
    mtmd_bitmap* bmp = wrap.bitmap;
    if (wrap.video_ctx) mtmd_helper_video_free(wrap.video_ctx);
    if (!bmp) { fail(env, "Could not read the image file."); return nullptr; }

    // LFM2 chat format (ChatML-style). BOS is added by the tokenizer.
    // Try the asked prompt first; if the engine refuses it, retry with the plain prompt that is known to work.
    // For each prompt, try the marker this context expects, then the default marker.
    mtmd_input_chunks* chunks = nullptr;
    const mtmd_bitmap* bitmaps[1] = {bmp};
    int32_t r = -1;
    const char* ctx_marker = mtmd_get_marker(c->mctx);
    const char* markers[2] = { ctx_marker, mtmd_default_marker() };
    const std::string prompts[2] = { pr, std::string("Describe this image in detail.") };
    std::string tried;
    g_last_log.clear();
    for (const std::string& p : prompts) {
        for (const char* marker : markers) {
            if (!marker) continue;
            std::string full = "<|im_start|>user\n" + std::string(marker) + "\n" + p +
                               "<|im_end|>\n<|im_start|>assistant\n";
            tried = full.substr(0, 60);
            mtmd_input_text txt;
            txt.text = full.c_str();
            txt.add_special = true;
            txt.parse_special = true;
            chunks = mtmd_input_chunks_init();
            r = mtmd_tokenize(c->mctx, chunks, &txt, bitmaps, 1);
            if (r == 0) break;
            mtmd_input_chunks_free(chunks);
            chunks = nullptr;
        }
        if (r == 0) break;
    }
    if (r != 0 || !chunks) {
        mtmd_bitmap_free(bmp);
        std::string diag = std::string(" [v5] ctx=[") + (ctx_marker ? ctx_marker : "null") +
                           "] def=[" + mtmd_default_marker() + "] last_text=[" + tried + "]";
        fail(env, "Could not process the image (tokenize error " + std::to_string(r) + "). " + g_last_log + diag);
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(c->lctx), true);
    llama_pos n_past = 0;
    r = mtmd_helper_eval_chunks(c->mctx, c->lctx, chunks, 0, 0, N_BATCH, true, &n_past);
    mtmd_input_chunks_free(chunks);
    mtmd_bitmap_free(bmp);
    if (r != 0) { fail(env, "Vision model failed to read the image (error " + std::to_string(r) + ")."); return nullptr; }

    const llama_vocab* vocab = llama_model_get_vocab(c->model);
    // low temperature = factual, fewer made-up details
    return to_jstring(env, sample_loop(c->lctx, vocab, 200, nullptr, 0.2f));
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadVisionModel(JNIEnv*, jobject, jlong ptr) {
    auto* c = reinterpret_cast<VisionCtx*>(ptr);
    if (!c) return;
    mtmd_free(c->mctx);
    llama_free(c->lctx);
    llama_model_free(c->model);
    delete c;
}

// ---------------- Speech (not built yet) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadSpeechModel(JNIEnv* env, jobject, jstring) {
    fail(env, "Voice (Whisper) is not built into this version yet.");
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_transcribe(JNIEnv* env, jobject, jlong, jstring) {
    fail(env, "Voice (Whisper) is not built into this version yet.");
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadSpeechModel(JNIEnv*, jobject, jlong) {}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_freeAll(JNIEnv*, jobject) { LOGI("freeAll"); }

} // extern "C"
