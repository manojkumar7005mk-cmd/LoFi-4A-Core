#include <jni.h>
#include <string>
#include <android/log.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LoFi-JNI", __VA_ARGS__)

extern "C" {

// Opaque handle so the Kotlin side keeps a stable jlong.
struct LofiCtx {
    int kind; // 1=text, 2=vision, 3=speech
    std::string path;
};

// ---------------- Text (llama.cpp) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadTextModel(
        JNIEnv* env, jobject /*thiz*/, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    __android_log_print(ANDROID_LOG_INFO, "LoFi-JNI", "loadTextModel: %s", p);
    auto* ctx = new LofiCtx{1, p};
    env->ReleaseStringUTFChars(path, p);
    // TODO: llama_model_load_from_file() + llama_init_from_model() here.
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_generate(
        JNIEnv* env, jobject /*thiz*/, jlong ctxPtr, jstring prompt, jint maxTokens) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr || ctx->kind != 1) {
        LOGE("generate: invalid text context");
        return env->NewStringUTF("");
    }
    const char* pr = env->GetStringUTFChars(prompt, nullptr);
    std::string out = "[stub] llama.cpp would generate for prompt: ";
    out += pr;
    env->ReleaseStringUTFChars(prompt, pr);
    (void)maxTokens;
    // TODO: real llama_tokenize / llama_decode loop.
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadTextModel(
        JNIEnv*, jobject, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr) return;
    // TODO: llama_free(ctx); llama_model_free(model);
    delete ctx;
}

// ---------------- Vision (llama.cpp mtmd) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadVisionModel(
        JNIEnv* env, jobject, jstring modelPath, jstring mmprojPath) {
    const char* m = env->GetStringUTFChars(modelPath, nullptr);
    const char* mm = env->GetStringUTFChars(mmprojPath, nullptr);
    __android_log_print(ANDROID_LOG_INFO, "LoFi-JNI",
                        "loadVisionModel: %s + %s", m, mm);
    auto* ctx = new LofiCtx{2, m};
    env->ReleaseStringUTFChars(modelPath, m);
    env->ReleaseStringUTFChars(mmprojPath, mm);
    // TODO: mtmd_init_from_file() here.
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_describeImage(
        JNIEnv* env, jobject, jlong ctxPtr, jstring imagePath, jstring prompt) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr || ctx->kind != 2) {
        LOGE("describeImage: invalid vision context");
        return env->NewStringUTF("");
    }
    env->GetStringUTFChars(imagePath, nullptr);
    env->ReleaseStringUTFChars(imagePath, env->GetStringUTFChars(imagePath, nullptr));
    const char* pr = env->GetStringUTFChars(prompt, nullptr);
    std::string out = "[stub] mtmd would describe image. Prompt: ";
    out += pr;
    env->ReleaseStringUTFChars(prompt, pr);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadVisionModel(JNIEnv*, jobject, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr) return;
    // TODO: mtmd_free(ctx);
    delete ctx;
}

// ---------------- Speech (whisper.cpp) ----------------

JNIEXPORT jlong JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_loadSpeechModel(
        JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    __android_log_print(ANDROID_LOG_INFO, "LoFi-JNI", "loadSpeechModel: %s", p);
    auto* ctx = new LofiCtx{3, p};
    env->ReleaseStringUTFChars(path, p);
    // TODO: whisper_init_from_file_with_params() here.
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_transcribe(
        JNIEnv* env, jobject, jlong ctxPtr, jstring wavPath) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr || ctx->kind != 3) {
        LOGE("transcribe: invalid speech context");
        return env->NewStringUTF("");
    }
    const char* w = env->GetStringUTFChars(wavPath, nullptr);
    __android_log_print(ANDROID_LOG_INFO, "LoFi-JNI", "transcribe: %s", w);
    env->ReleaseStringUTFChars(wavPath, w);
    // TODO: whisper_full() here.
    return env->NewStringUTF("[stub] whisper.cpp would transcribe audio");
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_unloadSpeechModel(JNIEnv*, jobject, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<LofiCtx*>(ctxPtr);
    if (ctx == nullptr) return;
    // TODO: whisper_free(ctx);
    delete ctx;
}

JNIEXPORT void JNICALL
Java_com_manoj_lofi4a_core_NativeBridge_freeAll(JNIEnv*, jobject) {
    // TODO: free any lingering ggml/llama/whisper allocations.
    __android_log_print(ANDROID_LOG_INFO, "LoFi-JNI", "freeAll");
}

} // extern "C"
