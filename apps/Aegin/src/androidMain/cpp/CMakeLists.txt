cmake_minimum_required(VERSION 3.22.1)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Default to sibling clone C:/IDE/Projects/llama.cpp (or C:/DEV/llama.cpp fallback)
set(DEFAULT_LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../llama.cpp")
if(NOT EXISTS "${DEFAULT_LLAMA_CPP_DIR}/CMakeLists.txt")
    if(EXISTS "C:/IDE/Projects/llama.cpp/CMakeLists.txt")
        set(DEFAULT_LLAMA_CPP_DIR "C:/IDE/Projects/llama.cpp")
    else()
        set(DEFAULT_LLAMA_CPP_DIR "C:/DEV/llama.cpp")
    endif()
endif()
set(LLAMA_CPP_DIR "${DEFAULT_LLAMA_CPP_DIR}" CACHE PATH "Path to local llama.cpp repository" FORCE)
file(TO_CMAKE_PATH "${LLAMA_CPP_DIR}" LLAMA_CPP_DIR)

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_CPP_DIR}. Verify path or pass -DLLAMA_CPP_DIR=...")
endif()

# 1. Disable desktop binaries, networking, and host-CPU flags before adding llama.cpp
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)

# Critical for cross-compiling on Windows x64 -> Android arm64-v8a / x86_64
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)

# 2. Out-of-tree source directories REQUIRE an explicit binary output directory
add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama_build")

# 3. Build the Aegin JNI shared library
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
)

target_compile_options(llama_jni PRIVATE
    -O3
    -fvisibility=hidden
    -Wall
    -Wextra
)

target_link_libraries(llama_jni PRIVATE
    llama
    ggml
    android
    log
)
