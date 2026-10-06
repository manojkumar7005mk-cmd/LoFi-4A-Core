cmake_minimum_required(VERSION 3.22.1)
project(lofi_jni C CXX)
set(CMAKE_CXX_STANDARD 17)

include(FetchContent)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS ON CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG v0.2.0
    GIT_SHALLOW TRUE)
FetchContent_GetProperties(llama)
if(NOT llama_POPULATED)
    FetchContent_Populate(llama)
    add_subdirectory(${llama_SOURCE_DIR} ${llama_BINARY_DIR} EXCLUDE_FROM_ALL)
endif()

add_library(lofi_jni SHARED lofi_jni.cpp)
find_library(log-lib log)
target_link_libraries(lofi_jni PRIVATE llama mtmd ${log-lib})
target_compile_options(lofi_jni PRIVATE -O2 -fvisibility=hidden)
