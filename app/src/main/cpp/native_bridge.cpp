#include <jni.h>

namespace {
constexpr char kStatus[] =
    "Biblioteca ARM64 carregada com sucesso. O runtime real de Shadow of the Colossus ainda nao foi integrado.";
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeRuntimeStatus(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF(kStatus);
}
