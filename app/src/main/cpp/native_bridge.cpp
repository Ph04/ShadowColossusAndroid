#include <jni.h>
#include <cstdlib>
#include <string>

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
    const bool valid = chars[0] != '\0';
    const int result = valid ? setenv("SOTC_ISO", chars, 1) : -1;
    env->ReleaseStringUTFChars(path, chars);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}
