cmake_minimum_required(VERSION 3.22.1)
project(sotc_android_bridge LANGUAGES C CXX)

# The pinned PS2Recomp submodule already has an Android/ARM64 runtime path.
# Keep host recompiler tools disabled in the APK build and compile its actual
# runtime and NativeActivity runner as separate native targets.
get_filename_component(SOTC_REPOSITORY_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(SOTC_UPSTREAM "${SOTC_REPOSITORY_ROOT}/upstream/sotc-vibe-pc")
set(SOTC_PS2RECOMP "${SOTC_UPSTREAM}/External/PS2Recomp")

if(NOT EXISTS "${SOTC_PS2RECOMP}/CMakeLists.txt")
    message(FATAL_ERROR "Pinned PS2Recomp submodule missing at ${SOTC_PS2RECOMP}; check out submodules recursively.")
endif()

set(PS2X_BUILD_RECOMP OFF CACHE BOOL "" FORCE)
set(PS2X_BUILD_RUNTIME ON CACHE BOOL "" FORCE)
set(PS2X_BUILD_ANALYZER OFF CACHE BOOL "" FORCE)
set(PS2X_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(PS2X_BUILD_STUDIO OFF CACHE BOOL "" FORCE)
set(PS2X_ENABLE_FFMPEG OFF CACHE BOOL "" FORCE)
set(PS2X_ENABLE_DEBUG_UI OFF CACHE BOOL "" FORCE)
set(PS2X_ENABLE_SCCACHE OFF CACHE BOOL "" FORCE)
set(PS2X_ENABLE_RUNNER_UNITY_BUILD ON CACHE BOOL "" FORCE)
set(PS2X_RUNNER_UNITY_BUILD_BATCH_SIZE 16 CACHE STRING "" FORCE)

add_subdirectory("${SOTC_PS2RECOMP}" "${CMAKE_CURRENT_BINARY_DIR}/ps2recomp")

add_library(sotc_android_bridge SHARED native_bridge.cpp)
target_compile_features(sotc_android_bridge PRIVATE cxx_std_17)
target_compile_options(sotc_android_bridge PRIVATE -Wall -Wextra -Werror=return-type)
find_library(android_log log)
target_link_libraries(sotc_android_bridge PRIVATE ${android_log})

if(TARGET ps2_runtime AND TARGET ps2EntryRunner)
    target_compile_definitions(sotc_android_bridge PRIVATE SOTC_PS2_RUNTIME_CONFIGURED=1)
endif()
