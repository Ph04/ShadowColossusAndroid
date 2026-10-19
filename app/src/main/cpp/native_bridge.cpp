#include <jni.h>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <unistd.h>

#if defined(SOTC_GAME_ENGINE_AVAILABLE)
namespace {
constexpr char kStatus[] =
    "Fontes do port de Shadow of the Colossus ligadas ao runner Android. "
    "A compatibilidade do renderer GS e o gameplay ainda precisam de teste real.";
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
    const std::filesystem::path appFilesDir = isoPath.parent_path().parent_path();
    std::error_code pathError;
    const bool valid = isoPath.is_absolute() &&
                       isoPath.filename() == "SCES-53326.iso" &&
                       std::filesystem::is_directory(appFilesDir, pathError) &&
                       !pathError;
    if (!valid || chdir(appFilesDir.c_str()) != 0) {
        env->ReleaseStringUTFChars(path, chars);
        return JNI_FALSE;
    }

    // NativeActivity runs in this app's process. Set writable private paths before it starts.
    const int isoResult = setenv("SOTC_ISO", isoPath.c_str(), 1);
    const std::string shaderCache = (appFilesDir / "game_data" / "gs_shaders").string();
    if (isoResult == 0) {
        setenv("PS2X_GS_SHADER_CACHE_DIR", shaderCache.c_str(), 0);
        setenv("PS2X_VU1_TIMING", "0", 0);
    }

    env->ReleaseStringUTFChars(path, chars);
    return isoResult == 0 ? JNI_TRUE : JNI_FALSE;
}
