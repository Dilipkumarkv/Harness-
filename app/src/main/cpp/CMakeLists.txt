cmake_minimum_required(VERSION 3.22.1)
project(harness_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_BUILD_TYPE Release CACHE STRING "Native inference requires optimization" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------------------------------------------------------------------------
# llama.cpp, pinned. Fetched at build time so the repository stays small; the
# tag is fixed so the C API used by harness_llama.cpp cannot drift.
# ---------------------------------------------------------------------------
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD       OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)

# CPU only: no Vulkan, no Metal, no ARM-specific kernel packs. Works on every
# arm64 device without depending on a driver.
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS  OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN            OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        v0.9.0
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama)

# ---------------------------------------------------------------------------
# Our JNI bridge.
# ---------------------------------------------------------------------------
add_library(harness_llama SHARED harness_llama.cpp)

target_link_libraries(harness_llama PRIVATE llama log)

# Android 15+ requires 16 KB page alignment for shared libraries.
target_link_options(harness_llama PRIVATE "-Wl,-z,max-page-size=16384")
