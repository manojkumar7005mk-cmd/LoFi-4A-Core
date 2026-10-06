# LoFi-4A Core

On-device multimodal AI for Android — text, vision and speech, all running locally.

- **Text:** Gemma 3 1B Instruct (GGUF Q4_K_M) via llama.cpp
- **Vision:** LFM2-VL 450M (GGUF + mmproj) via llama.cpp + mtmd
- **Speech:** Whisper Base via whisper.cpp

Designed and developed by **Manoj Kumar**, a student.

## Features
- Fully offline inference (models download once, then run on-device)
- Background downloads and inference via foreground service
- Safe model load/unload with native memory cleanup; models are never all loaded at once
- Offline indicator + per-model download/load status
- Compose UI with Chat, Models and About screens

## Build locally
1. Install Android Studio + SDK 34, NDK (CMake 3.22.1)
2. `gradle wrapper --gradle-version 8.9` (or open in Android Studio, which generates it)
3. `./gradlew assembleDebug`

## Build on GitHub Actions
Push to `main`. The workflow in `.github/workflows/android-build.yml` builds a
debug APK on every push/PR and a release APK on pushes to `main`.
Artifacts are uploaded automatically — no signing secrets required for the debug build.

### Signing the release build (optional)
Add a `KEYSTORE_BASE64` secret (base64 of your release keystore). The workflow
decodes it to `app/release.keystore` before building.

## Before release
- Replace the placeholder `downloadUrl` values in `ModelDefinition.BUILTINS`
  with real GGUF model URLs.
- Replace the stub bodies in `app/src/main/cpp/lofi_jni.cpp` with real
  llama.cpp / mtmd / whisper.cpp calls (see comments in `CMakeLists.txt`).

## Licenses
llama.cpp and whisper.cpp are MIT licensed. Gemma 3 is Apache 2.0 with its own
usage terms. See the in-app About screen for the full list.
