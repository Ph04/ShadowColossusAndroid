#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <unistd.h>

#if defined(SOTC_GAME_ENGINE_AVAILABLE)
namespace {
constexpr char kStatus[] =
    "Fontes do jogo ligadas ao runner Android. GS por software como fallback; controles touch "
    "integrados ao controle PS2. Boot, imagem, áudio e desempenho ainda precisam de teste real.";
}
#else
namespace {
constexpr char kStatus[] =
#if defined(SOTC_PS2_RUNTIME_CONFIGURED)
    "Runtime PS2Recomp ARM64 configurado. Fontes C++ geradas do jogo ausentes; "
    "o APK atual ainda não é jogável.";
#else
    "Runtime PS2Recomp não configurado; somente a ponte JNI está disponível.";
#endif
}
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeRuntimeStatus(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF(kStatus);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeEngineAvailable(JNIEnv*, jobject /* thiz */) {
#if defined(SOTC_GAME_ENGINE_AVAILABLE)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeSetIsoPath(JNIEnv* env, jobject /* thiz */, jstring path) {
    if (path == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return JNI_FALSE;

    const std::filesystem::path isoPath(chars);
    __android_log_print(ANDROID_LOG_INFO, "SOTC_DIAG", "nativeSetIsoPath called; path=%s", chars);
    const std::filesystem::path appFilesDir = isoPath.parent_path().parent_path();
    std::error_code pathError;
    const bool valid = isoPath.is_absolute() &&
                       isoPath.filename() == "SCES-53326.iso" &&
                       std::filesystem::is_directory(appFilesDir, pathError) &&
                       !pathError;
    if (!valid || chdir(appFilesDir.c_str()) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "SOTC_DIAG", "ISO path preflight failed; valid_path=%d; filename_ok=%d",
                            valid ? 1 : 0, isoPath.filename() == "SCES-53326.iso" ? 1 : 0);
        env->ReleaseStringUTFChars(path, chars);
        return JNI_FALSE;
    }

    // NativeActivity runs in this app's process. Set writable private paths before it starts.
    const int isoResult = setenv("SOTC_ISO", isoPath.c_str(), 1);
    // The current GS GPU backend requires desktop GL 4.6 compute shaders and
    // ARB_fragment_shader_interlock. Keep Android on the known software GS
    // rasterizer until an accelerated mobile backend is implemented and tested.
    const int rendererResult = setenv("PS2X_GS_GPU", "0", 1);
    const int workerResult = setenv("PS2X_GS_THREAD", "1", 1);
    const std::string shaderCache = (appFilesDir / "game_data" / "gs_shaders").string();
    if (isoResult == 0 && rendererResult == 0 && workerResult == 0) {
        setenv("PS2X_GS_SHADER_CACHE_DIR", shaderCache.c_str(), 0);
        setenv("PS2X_VU1_TIMING", "0", 0);
    }

    __android_log_print(isoResult == 0 && rendererResult == 0 && workerResult == 0 ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
                        "SOTC_DIAG", "launch environment configured; iso=%d renderer=%d gs_thread=%d",
                        isoResult, rendererResult, workerResult);
    env->ReleaseStringUTFChars(path, chars);
    return isoResult == 0 && rendererResult == 0 && workerResult == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeDiagnosticReport(JNIEnv* env, jobject /* thiz */) {
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
    report += "ps2_runtime=configured\n";
#else
    report += "ps2_runtime=not_configured\n";
#endif
#if defined(SOTC_GAME_ENGINE_AVAILABLE)
    report += "generated_game_engine=linked\n";
    report += "play_gate=engine_available\n";
#else
    report += "generated_game_engine=missing\n";
    report += "play_gate=blocked_because_generated_sources_were_not_linked\n";
#endif
    report += "renderer_policy=software_gs_fallback_on_android\n";
    report += "iso_integrity=not_validated_by_launcher_size_only\n";
    __android_log_print(ANDROID_LOG_INFO, "SOTC_DIAG", "native diagnostic report requested");
    return env->NewStringUTF(report.c_str());
}

