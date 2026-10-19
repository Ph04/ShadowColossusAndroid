#include <jni.h>

#if defined(SOTC_PS2_RUNTIME_CONFIGURED)
namespace {
constexpr char kStatus[] =
    "A biblioteca JNI foi carregada. O runtime PS2Recomp ARM64 e seu runner Android "
    "fazem parte da compilacao; o codigo recompilado especifico de Shadow of the Colossus "
    "e o renderizador adaptado ainda nao foram integrados.";
}
#else
namespace {
constexpr char kStatus[] =
    "A biblioteca JNI foi carregada, mas o runtime PS2Recomp nao foi configurado.";
}
#endif

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mobile_sotcandroid_MainActivity_nativeRuntimeStatus(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF(kStatus);
}
