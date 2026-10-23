#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <unistd.h>

namespace {
constexpr char kTag[] = "SOTC_DIAG";
constexpr char kBootName[] = "SCUS_974.72";

bool requiredDiscFilesExist(const std::filesystem::path& discDirectory) {
    constexpr const char* required[] = {
        "SCUS_974.72", "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF"
    };
    std::error_code error;
    for (const char* name : required) {
        const auto candidate = discDirectory / name;
        if (!std::filesystem::is_regular_file(candidate, error) || error ||
            std::filesystem::file_size(candidate, error) == 0u || error) {
            return false;
        }
    }
    return true;
}

#if defined(SOTC_GAME_ENGINE_AVAILABLE)
constexpr char kStatus[] =
    "Celosia USA game code is linked into this APK. Android uses the emulated PS2 GS software path; boot, image, controls and audio still require device testing.";
#else
constexpr char kStatus[] =
#if defined(SOTC_PS2_RUNTIME_CONFIGURED)
    "Celosia PS2Recomp runtime is configured for ARM64 Android, but the USA game code generated from the user's ISO is absent; this APK is diagnostic only.";
#else
    "Celosia PS2Recomp runtime is not configured; only the diagnostic JNI bridge is available.";
#endif
#endif
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeRuntimeStatus(JNIEnv* env, jobject) {
    return env->NewStringUTF(kStatus);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeEngineAvailable(JNIEnv*, jobject) {
#if defined(SOTC_GAME_ENGINE_AVAILABLE)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeSetIsoPath(JNIEnv* env, jobject, jstring path) {
    if (path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;

    const std::filesystem::path bootElf(chars);
    const std::filesystem::path discDirectory = bootElf.parent_path();
    const std::filesystem::path appFilesDirectory =
        discDirectory.parent_path().parent_path();
    __android_log_print(ANDROID_LOG_INFO, kTag, "nativeSetIsoPath called; boot=%s", chars);

    std::error_code pathError;
    const bool valid = bootElf.is_absolute() &&
                       bootElf.filename() == kBootName &&
                       std::filesystem::is_directory(appFilesDirectory, pathError) &&
                       !pathError &&
                       requiredDiscFilesExist(discDirectory);
    if (!valid || chdir(appFilesDirectory.c_str()) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "USA disc preflight failed; valid_path=%d; boot_name_ok=%d; required_files_ok=%d",
                            valid ? 1 : 0,
                            bootElf.filename() == kBootName ? 1 : 0,
                            requiredDiscFilesExist(discDirectory) ? 1 : 0);
        env->ReleaseStringUTFChars(path, chars);
        return JNI_FALSE;
    }

    // Celosia's compiled default ELF is relative to this private app-files cwd.
    // Until an Android native renderer exists, use the emulated PS2 GS path.
    const int bootResult = setenv("PS2X_DEFAULT_BOOT_ELF", "game/disc/SCUS_974.72", 1);
    const int gpuResult = setenv("PS2X_GS_GPU", "0", 1);
    const int threadResult = setenv("PS2X_GS_THREADS", "2", 1);
    const int nativeRendererResult = setenv("SOTC_NATIVE", "0", 1);
    const std::filesystem::path memoryCardDirectory = appFilesDirectory / "memcards";
    std::error_code directoryError;
    std::filesystem::create_directories(memoryCardDirectory, directoryError);
    const int memoryCardResult = setenv("PS2X_MEMCARD_DIR", memoryCardDirectory.c_str(), 1);

    __android_log_print(bootResult == 0 && gpuResult == 0 && threadResult == 0 &&
                                nativeRendererResult == 0 && memoryCardResult == 0 && !directoryError
                            ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
                        kTag,
                        "Celosia launch env; boot=%d gpu=%d gs_threads=%d native=%d memcard=%d memcard_dir_error=%d",
                        bootResult, gpuResult, threadResult, nativeRendererResult, memoryCardResult,
                        directoryError ? 1 : 0);
    env->ReleaseStringUTFChars(path, chars);
    return bootResult == 0 && gpuResult == 0 && threadResult == 0 &&
                   nativeRendererResult == 0 && memoryCardResult == 0 && !directoryError
               ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeDiagnosticReport(JNIEnv* env, jobject) {
    std::string report;
    report += "native_bridge=loaded\n";
#if defined(__aarch64__)
    report += "native_abi=aarch64\n";
#else
    report += "native_abi=unknown\n";
#endif
#if defined(__ANDROID_API__)
    report += "android_api_at_compile=" + std::to_string(__ANDROID_API__) + "\n";
#endif
#ifdef __clang_version__
    report += "compiler=" __clang_version__ "\n";
#endif
#if defined(SOTC_PS2_RUNTIME_CONFIGURED)
    report += "ps2_runtime=configured_from_celosia\n";
#else
    report += "ps2_runtime=not_configured\n";
#endif
#if defined(SOTC_GAME_ENGINE_AVAILABLE)
    report += "generated_usa_game_engine=linked\n";
    report += "play_gate=celosia_generated_game_linked\n";
#else
    report += "generated_usa_game_engine=missing\n";
    report += "play_gate=blocked_until_local_usa_codegen_and_link\n";
#endif
    report += "game_edition=USA_SCUS_97472\n";
    report += "renderer_policy=software_ps2_gs_fallback\n";
    report += "iso_validation=ISO9660_structure_and_required_files; source_hash_is_not_official_authenticity_proof\n";
    __android_log_print(ANDROID_LOG_INFO, kTag, "native diagnostic report requested");
    return env->NewStringUTF(report.c_str());
}
