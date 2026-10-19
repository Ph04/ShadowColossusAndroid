cmake_minimum_required(VERSION 3.22.1)
project(sotc_android_bridge LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Source port + PS2Recomp are pinned as the upstream submodule.
get_filename_component(SOTC_REPOSITORY_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(SOTC_UPSTREAM "${SOTC_REPOSITORY_ROOT}/upstream/sotc-vibe-pc")
set(SOTC_PS2RECOMP "${SOTC_UPSTREAM}/External/PS2Recomp")
set(SOTC_PORT "${SOTC_UPSTREAM}/Port")
set(SOTC_GENERATED "${SOTC_PORT}/generated")

# The upstream controller poller reads raylib keyboard/gamepad state. Apply a
# small, idempotent Android-only hook so touch controls feed the same PS2 pad
# state rather than synthesizing unreliable Android key events.
find_package(Python3 COMPONENTS Interpreter REQUIRED)
set(SOTC_ANDROID_TOUCH_PATCH "${SOTC_REPOSITORY_ROOT}/scripts/patch_ps2recomp_android_input.py")
set(SOTC_PS2_HOST_INPUT "${SOTC_PS2RECOMP}/ps2xRuntime/src/lib/ps2_host_input.cpp")
if(NOT EXISTS "${SOTC_ANDROID_TOUCH_PATCH}" OR NOT EXISTS "${SOTC_PS2_HOST_INPUT}")
    message(FATAL_ERROR "Android touch-input patch prerequisites are missing.")
endif()
execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_ANDROID_TOUCH_PATCH}" --source "${SOTC_PS2_HOST_INPUT}"
    RESULT_VARIABLE SOTC_TOUCH_PATCH_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_TOUCH_PATCH_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not patch the pinned PS2Recomp host input source for Android touch controls.")
endif()

# This shared library stores touch state exactly once in the app process. Both
# the Kotlin touch overlay and PS2Recomp's controller poller use this library.
add_library(sotc_android_touch SHARED android_touch_input.cpp)
target_include_directories(sotc_android_touch PUBLIC "${CMAKE_CURRENT_LIST_DIR}")
target_compile_features(sotc_android_touch PRIVATE cxx_std_17)
target_compile_options(sotc_android_touch PRIVATE -Wall -Wextra -Werror=return-type)

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

if(TARGET ps2_runtime)
    target_include_directories(ps2_runtime BEFORE PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
    target_link_libraries(ps2_runtime PUBLIC sotc_android_touch)
endif()

# Several runtime files include Intel intrinsic header names directly.
# On AArch64 redirect these legacy names to the upstream sse2neon compatibility
# header instead of Clang's x86-only intrinsic headers.
set(SOTC_ARM_SSE_SHIM_DIR "${CMAKE_CURRENT_LIST_DIR}/arm_sse_shims")
if(TARGET ps2_runtime)
    target_include_directories(ps2_runtime BEFORE PRIVATE "${SOTC_ARM_SSE_SHIM_DIR}")
endif()
if(TARGET ps2EntryRunner)
    target_include_directories(ps2EntryRunner BEFORE PRIVATE "${SOTC_ARM_SSE_SHIM_DIR}")
endif()

# The upstream CMake file creates a generic PS2 ELF runner. If the user has
# generated Shadow of the Colossus C++ from their own supported ISO, replace
# only that runner's main() and attach the game-specific hooks and code.
# Generated game code is intentionally not tracked or shipped in this repo.
set(SOTC_GAME_ENGINE_AVAILABLE OFF)
if(EXISTS "${SOTC_GENERATED}/register_functions.cpp" AND
   EXISTS "${SOTC_GENERATED}/sotc_layout_generated.h")
    message(STATUS "Shadow of the Colossus generated code found: wiring game host layer into ps2EntryRunner")

    set(SOTC_HOST_SOURCES
        "${SOTC_PORT}/src/sotc/config.cpp"
        "${SOTC_PORT}/src/sotc/debug_script.cpp"
        "${SOTC_PORT}/src/sotc/display_mode.cpp"
        "${SOTC_PORT}/src/sotc/function_hooks.cpp"
        "${SOTC_PORT}/src/sotc/game_disc.cpp"
        "${SOTC_PORT}/src/sotc/hle/sce_cdvd.cpp"
        "${SOTC_PORT}/src/sotc/hle/libgcc.cpp"
        "${SOTC_PORT}/src/sotc/hle/sce_fileio.cpp"
        "${SOTC_PORT}/src/sotc/idle_thread.cpp"
        "${SOTC_PORT}/src/sotc/log.cpp"
        "${SOTC_PORT}/src/sotc/module_guard.cpp"
        "${SOTC_PORT}/src/sotc/motion_blur.cpp"
        "${SOTC_PORT}/src/sotc/mouse_camera.cpp"
        "${SOTC_PORT}/src/sotc/sha256.cpp"
        "${SOTC_PORT}/src/sotc/window_icon.cpp"
        "${SOTC_PORT}/src/sotc/watchdog.cpp"
    )
    foreach(source IN LISTS SOTC_HOST_SOURCES)
        if(NOT EXISTS "${source}")
            message(FATAL_ERROR "Missing required Shadow of the Colossus port source: ${source}")
        endif()
    endforeach()

    file(GLOB SOTC_GENERATED_SOURCES CONFIGURE_DEPENDS "${SOTC_GENERATED}/*.cpp")
    add_library(sotc_game_code STATIC ${SOTC_GENERATED_SOURCES})
    target_include_directories(sotc_game_code PUBLIC "${SOTC_GENERATED}")
    target_link_libraries(sotc_game_code PUBLIC ps2_runtime)
    target_include_directories(sotc_game_code BEFORE PRIVATE "${SOTC_ARM_SSE_SHIM_DIR}")
    set_target_properties(sotc_game_code PROPERTIES UNITY_BUILD OFF)

    # The upstream PC build precompiles the runtime/generated headers for this
    # large target. Keep the same strategy on NDK/Clang to avoid reparsing the
    # runtime API thousands of times during a first local ISO build.
    target_precompile_headers(sotc_game_code PRIVATE
        "${SOTC_PS2RECOMP}/ps2xRuntime/include/ps2_runtime_macros.h"
        "${SOTC_PS2RECOMP}/ps2xRuntime/include/ps2_runtime.h"
        "${SOTC_GENERATED}/ps2_recompiled_functions.h"
        "${SOTC_GENERATED}/ps2_recompiled_stubs.h"
        "${SOTC_PS2RECOMP}/ps2xRuntime/include/ps2_syscalls.h"
        "${SOTC_PS2RECOMP}/ps2xRuntime/include/ps2_stubs.h"
    )

    # register_functions.cpp can be an exceptionally large generated TU.
    # Disable optimizer passes for this registration table to reduce peak
    # memory during Android builds; gameplay functions remain optimized.
    set_source_files_properties(
        "${SOTC_GENERATED}/register_functions.cpp"
        PROPERTIES COMPILE_OPTIONS "-O0"
    )

    # Replace the generic runner entry point with the game-specific boot path.
    # PS2Recomp prefers src/runner/main.cpp when present and only falls
    # back to src/main.cpp on older revisions. Remove both variants so the
    # Android shared library has exactly the SOTC boot entry point.
    set(SOTC_GENERIC_MAIN_SOURCES
        "${SOTC_PS2RECOMP}/ps2xRuntime/src/runner/main.cpp"
        "${SOTC_PS2RECOMP}/ps2xRuntime/src/main.cpp"
    )
    set(SOTC_GENERIC_MAIN_NORMALIZED_LIST)
    foreach(SOTC_GENERIC_MAIN IN LISTS SOTC_GENERIC_MAIN_SOURCES)
        file(TO_CMAKE_PATH "${SOTC_GENERIC_MAIN}" SOTC_GENERIC_MAIN_NORMALIZED)
        list(APPEND SOTC_GENERIC_MAIN_NORMALIZED_LIST "${SOTC_GENERIC_MAIN_NORMALIZED}")
    endforeach()
    get_target_property(SOTC_RUNNER_SOURCES ps2EntryRunner SOURCES)
    set(SOTC_RUNNER_SOURCES_FILTERED)
    foreach(SOTC_SOURCE IN LISTS SOTC_RUNNER_SOURCES)
        set(SOTC_SOURCE_NORMALIZED "${SOTC_SOURCE}")
        if(NOT IS_ABSOLUTE "${SOTC_SOURCE}")
            if(EXISTS "${SOTC_PS2RECOMP}/${SOTC_SOURCE}")
                get_filename_component(SOTC_SOURCE_NORMALIZED "${SOTC_PS2RECOMP}/${SOTC_SOURCE}" ABSOLUTE)
            elseif(EXISTS "${SOTC_PS2RECOMP}/ps2xRuntime/${SOTC_SOURCE}")
                get_filename_component(SOTC_SOURCE_NORMALIZED "${SOTC_PS2RECOMP}/ps2xRuntime/${SOTC_SOURCE}" ABSOLUTE)
            endif()
        endif()
        file(TO_CMAKE_PATH "${SOTC_SOURCE_NORMALIZED}" SOTC_SOURCE_NORMALIZED)
        set(SOTC_IS_GENERIC_MAIN OFF)
        foreach(SOTC_GENERIC_MAIN_NORMALIZED IN LISTS SOTC_GENERIC_MAIN_NORMALIZED_LIST)
            if(SOTC_SOURCE_NORMALIZED STREQUAL SOTC_GENERIC_MAIN_NORMALIZED)
                set(SOTC_IS_GENERIC_MAIN ON)
            endif()
        endforeach()
        if(NOT SOTC_IS_GENERIC_MAIN
           AND NOT SOTC_SOURCE MATCHES "(^|/)ps2xRuntime/src/(runner/)?main\\.cpp$")
            list(APPEND SOTC_RUNNER_SOURCES_FILTERED "${SOTC_SOURCE}")
        endif()
    endforeach()
    set_property(TARGET ps2EntryRunner PROPERTY SOURCES "${SOTC_RUNNER_SOURCES_FILTERED}")
    target_sources(ps2EntryRunner PRIVATE "${SOTC_PORT}/src/main.cpp" ${SOTC_HOST_SOURCES})
    target_include_directories(ps2EntryRunner PRIVATE "${SOTC_PORT}/src" "${SOTC_GENERATED}")
    target_link_libraries(ps2EntryRunner PRIVATE sotc_game_code)

    file(GLOB SOTC_VU1_SOURCES CONFIGURE_DEPENDS "${SOTC_GENERATED}/vu1/*.cpp")
    if(SOTC_VU1_SOURCES)
        add_library(sotc_vu1_code STATIC ${SOTC_VU1_SOURCES})
        target_include_directories(sotc_vu1_code PRIVATE "${SOTC_PS2RECOMP}/ps2xRuntime/src/lib/vu" "${SOTC_GENERATED}")
        target_link_libraries(sotc_vu1_code PUBLIC ps2_runtime)
        target_include_directories(sotc_vu1_code BEFORE PRIVATE "${SOTC_ARM_SSE_SHIM_DIR}")
        target_link_libraries(ps2EntryRunner PRIVATE sotc_vu1_code)
        target_compile_definitions(ps2EntryRunner PRIVATE SOTC_HAS_VU1_PROGRAMS=1)
    endif()

    set(SOTC_GAME_ENGINE_AVAILABLE ON)
else()
    message(STATUS "SOTC generated C++ is absent; building runtime diagnostics only, not a playable game")
endif()

add_library(sotc_android_bridge SHARED native_bridge.cpp)
target_compile_features(sotc_android_bridge PRIVATE cxx_std_17)
target_compile_options(sotc_android_bridge PRIVATE -Wall -Wextra -Werror=return-type)
find_library(android_log log)
target_link_libraries(sotc_android_bridge PRIVATE ${android_log})

if(TARGET ps2_runtime AND TARGET ps2EntryRunner)
    target_compile_definitions(sotc_android_bridge PRIVATE SOTC_PS2_RUNTIME_CONFIGURED=1)
endif()
if(SOTC_GAME_ENGINE_AVAILABLE)
    target_compile_definitions(sotc_android_bridge PRIVATE SOTC_GAME_ENGINE_AVAILABLE=1)
endif()
