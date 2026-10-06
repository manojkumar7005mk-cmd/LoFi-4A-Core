cmake_minimum_required(VERSION 3.22.1)
project(lofi_jni CXX)

# ---------------------------------------------------------------------------
# llama.cpp / whisper.cpp integration.
#
# The JNI glue below is buildable as-is. To link the real engines, fetch
# them at configure time (pinned) and add_subdirectory() them:
#
#   FetchContent_Declare(llama GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
#                        GIT_TAG <pinned-commit>)
#   FetchContent_MakeAvailable(llama)
#   target_link_libraries(lofi_jni PRIVATE llama mtmd whisper)
#
# Then replace the stub bodies with real llama_*/whisper_* calls.
# ---------------------------------------------------------------------------

add_library(lofi_jni SHARED lofi_jni.cpp)

find_library(log-lib log)
target_link_libraries(lofi_jni PRIVATE ${log-lib})
target_compile_options(lofi_jni PRIVATE -O2 -fvisibility=hidden)
