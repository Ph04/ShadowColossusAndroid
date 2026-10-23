cmake_minimum_required(VERSION 3.22.1)
project(sotc_android_bridge LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(SOTC_REPOSITORY_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(SOTC_CELOSIA_CHECKOUT "${SOTC_REPOSITORY_ROOT}/upstream/celosia")
set(SOTC_CELOSIA_SOURCE "${SOTC_REPOSITORY_ROOT}/build/celosia-source")
set(SOTC_CELOSIA_BUILD_DIR "${SOTC_REPOSITORY_ROOT}/build/celosia")
set(SOTC_CELOSIA_GENERATED "${SOTC_CELOSIA_BUILD_DIR}/runner")
set(SOTC_CELOSIA_REVISION "ed1025701cfa943d7c1649d88e5175a51ff33d5e")
find_package(Python3 COMPONENTS Interpreter REQUIRED)

# Keep the cloned, pinned upstream clean. Android-only changes are applied to a
# disposable source mirror under build/, never to the Celosia checkout or main.
execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_REPOSITORY_ROOT}/scripts/ensure_celosia.py"
            --destination "${SOTC_CELOSIA_CHECKOUT}" --revision "${SOTC_CELOSIA_REVISION}"
    RESULT_VARIABLE SOTC_CELOSIA_CHECK_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_CELOSIA_CHECK_RESULT EQUAL 0)
    message(FATAL_ERROR "Celosia checkout validation failed. See the previous script output.")
endif()

execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_REPOSITORY_ROOT}/scripts/prepare_celosia_android_source.py"
            --source "${SOTC_CELOSIA_CHECKOUT}"
            --destination "${SOTC_CELOSIA_SOURCE}"
            --revision "${SOTC_CELOSIA_REVISION}"
    RESULT_VARIABLE SOTC_CELOSIA_MIRROR_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_CELOSIA_MIRROR_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not create the pinned Celosia source mirror for Android.")
endif()

set(SOTC_ANDROID_TOUCH_PATCH "${SOTC_REPOSITORY_ROOT}/scripts/patch_celosia_android_input.py")
set(SOTC_CELOSIA_PAD_SOURCE "${SOTC_CELOSIA_SOURCE}/PS2Recomp/ps2xRuntime/src/lib/ps2_pad.cpp")
if(NOT EXISTS "${SOTC_ANDROID_TOUCH_PATCH}" OR NOT EXISTS "${SOTC_CELOSIA_PAD_SOURCE}")
    message(FATAL_ERROR "Celosia Android touch patch prerequisites are missing.")
endif()
execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_ANDROID_TOUCH_PATCH}" --source "${SOTC_CELOSIA_PAD_SOURCE}"
    RESULT_VARIABLE SOTC_TOUCH_PATCH_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_TOUCH_PATCH_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not apply the Android touch bridge to Celosia's PS2 pad poller.")
endif()

set(SOTC_ANDROID_PATH_PATCH "${SOTC_REPOSITORY_ROOT}/scripts/patch_celosia_android_paths.py")
set(SOTC_CELOSIA_RUNTIME_SOURCE "${SOTC_CELOSIA_SOURCE}/PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp")
if(NOT EXISTS "${SOTC_ANDROID_PATH_PATCH}" OR NOT EXISTS "${SOTC_CELOSIA_RUNTIME_SOURCE}")
    message(FATAL_ERROR "Celosia Android persistent memory-card path patch prerequisites are missing.")
endif()
execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_ANDROID_PATH_PATCH}" --source "${SOTC_CELOSIA_RUNTIME_SOURCE}"
    RESULT_VARIABLE SOTC_PATH_PATCH_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_PATH_PATCH_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not route the Celosia memory-card root to persistent app storage.")
endif()

set(SOTC_ANDROID_STARTUP_PATCH "${SOTC_REPOSITORY_ROOT}/scripts/patch_celosia_android_startup.py")
set(SOTC_CELOSIA_MAIN_SOURCE "${SOTC_CELOSIA_SOURCE}/PS2Recomp/ps2xRuntime/src/main.cpp")
if(NOT EXISTS "${SOTC_ANDROID_STARTUP_PATCH}" OR NOT EXISTS "${SOTC_CELOSIA_MAIN_SOURCE}")
    message(FATAL_ERROR "Celosia Android startup patch prerequisites are missing.")
endif()
execute_process(
    COMMAND "${Python3_EXECUTABLE}" "${SOTC_ANDROID_STARTUP_PATCH}" --source "${SOTC_CELOSIA_MAIN_SOURCE}"
    RESULT_VARIABLE SOTC_STARTUP_PATCH_RESULT
    COMMAND_ECHO STDOUT
)
if(NOT SOTC_STARTUP_PATCH_RESULT EQUAL 0)
    message(FATAL_ERROR "Could not patch Celosia's boot path and persistent startup diagnostics.")
endif()

# The JNI bridge and the game runtime share one process-wide touch state.
add_library(sotc_android_touch SHARED android_touch_input.cpp)
target_include_directories(sotc_android_touch PUBLIC "${CMAKE_CURRENT_LIST_DIR}")
target_compile_features(sotc_android_touch PRIVATE cxx_std_17)
target_compile_options(sotc_android_touch PRIVATE -Wall -Wextra -Werror=return-type)

if(NOT EXISTS "${SOTC_CELOSIA_SOURCE}/PS2Recomp/CMakeLists.txt")
    message(FATAL_ERROR "The pinned Celosia PS2Recomp tree is missing.")
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
set(PS2X_ENABLE_RUNNER_PCH ON CACHE BOOL "" FORCE)

# This path is relative to the app-private files directory. The JNI bridge
# sets cwd before GameActivity/NativeActivity starts.
set(PS2X_DEFAULT_BOOT_ELF "game/disc/SCUS_974.72" CACHE STRING "" FORCE)

file(GLOB SOTC_CELOSIA_GAME_SOURCES CONFIGURE_DEPENDS "${SOTC_CELOSIA_GENERATED}/*.cpp")
set(SOTC_GAME_ENGINE_AVAILABLE OFF)
set(SOTC_RUNNER_DIRS "${SOTC_CELOSIA_GENERATED}")

if(SOTC_CELOSIA_GAME_SOURCES)
    # Only game-specific overrides may be included once generated USA functions
    # exist. A clean CI checkout builds diagnostics, not fake game code.
    list(APPEND SOTC_RUNNER_DIRS
        "${SOTC_CELOSIA_BUILD_DIR}/vu1rec"
        "${SOTC_CELOSIA_SOURCE}/game/overrides"
    )
    set(SOTC_GAME_ENGINE_AVAILABLE ON)
    message(STATUS "Celosia USA generated game C++ found; linking the native game overrides.")
else()
    message(STATUS "Celosia generated USA C++ is absent; building runtime diagnostics only.")
endif()
set(PS2X_RUNNER_DIRS "${SOTC_RUNNER_DIRS}" CACHE STRING "" FORCE)

# Celosia's Android-aware CMake creates ps2EntryRunner as a shared library,
# links raylib Android + log/android, and configures ANativeActivity_onCreate.
add_subdirectory("${SOTC_CELOSIA_SOURCE}/PS2Recomp" "${CMAKE_CURRENT_BINARY_DIR}/ps2recomp")

# A clean CI checkout has no user's generated game C++. Celosia's runtime still
# references the generated function table, so provide a zero-range stub only
# for diagnostic builds. Never compile this alongside real generated functions.
if(NOT SOTC_GAME_ENGINE_AVAILABLE)
    target_sources(ps2EntryRunner PRIVATE "${CMAKE_CURRENT_LIST_DIR}/empty_recompiled_function_table.cpp")
    message(STATUS "No generated USA code: linking the zero-range diagnostic function table.")
endif()


if(TARGET ps2_runtime)
    target_include_directories(ps2_runtime BEFORE PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
    target_link_libraries(ps2_runtime PUBLIC sotc_android_touch)
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
