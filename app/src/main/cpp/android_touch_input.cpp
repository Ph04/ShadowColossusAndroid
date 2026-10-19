#include "sotc_android_touch.h"

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>

namespace {
std::atomic<uint32_t> g_buttons{0};
std::atomic<float> g_left_x{0.0f};
std::atomic<float> g_left_y{0.0f};
std::atomic<float> g_right_x{0.0f};
std::atomic<float> g_right_y{0.0f};
std::atomic<bool> g_left_active{false};
std::atomic<bool> g_right_active{false};

float clampAxis(float value) {
    if (!std::isfinite(value)) return 0.0f;
    return std::clamp(value, -1.0f, 1.0f);
}
}

extern "C" void sotcAndroidTouchSetButton(uint32_t buttonMask, int pressed) {
    buttonMask &= 0xFFFFu;
    uint32_t oldValue = g_buttons.load(std::memory_order_relaxed);
    uint32_t nextValue;
    do {
        nextValue = pressed ? (oldValue | buttonMask) : (oldValue & ~buttonMask);
    } while (!g_buttons.compare_exchange_weak(oldValue, nextValue,
                                               std::memory_order_release,
                                               std::memory_order_relaxed));
}

extern "C" void sotcAndroidTouchSetStick(uint32_t stick, float x, float y, int active) {
    const float safeX = active ? clampAxis(x) : 0.0f;
    const float safeY = active ? clampAxis(y) : 0.0f;
    if (stick == 0u) {
        g_left_x.store(safeX, std::memory_order_relaxed);
        g_left_y.store(safeY, std::memory_order_relaxed);
        g_left_active.store(active != 0, std::memory_order_release);
    } else if (stick == 1u) {
        g_right_x.store(safeX, std::memory_order_relaxed);
        g_right_y.store(safeY, std::memory_order_relaxed);
        g_right_active.store(active != 0, std::memory_order_release);
    }
}

extern "C" void sotcAndroidTouchReset() {
    g_buttons.store(0u, std::memory_order_release);
    sotcAndroidTouchSetStick(0u, 0.0f, 0.0f, 0);
    sotcAndroidTouchSetStick(1u, 0.0f, 0.0f, 0);
}

extern "C" void sotcAndroidTouchRead(uint16_t* buttons,
                                      float* leftX, float* leftY,
                                      float* rightX, float* rightY,
                                      bool* leftActive, bool* rightActive) {
    if (buttons) *buttons = static_cast<uint16_t>(g_buttons.load(std::memory_order_acquire) & 0xFFFFu);
    if (leftX) *leftX = g_left_x.load(std::memory_order_relaxed);
    if (leftY) *leftY = g_left_y.load(std::memory_order_relaxed);
    if (rightX) *rightX = g_right_x.load(std::memory_order_relaxed);
    if (rightY) *rightY = g_right_y.load(std::memory_order_relaxed);
    if (leftActive) *leftActive = g_left_active.load(std::memory_order_acquire);
    if (rightActive) *rightActive = g_right_active.load(std::memory_order_acquire);
}

#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_dev_mobile_sotcandroid_GameActivity_nativeSetTouchButton(
    JNIEnv*, jobject, jint buttonMask, jboolean pressed) {
    sotcAndroidTouchSetButton(static_cast<uint32_t>(buttonMask), pressed == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mobile_sotcandroid_GameActivity_nativeSetTouchStick(
    JNIEnv*, jobject, jint stick, jfloat x, jfloat y, jboolean active) {
    sotcAndroidTouchSetStick(static_cast<uint32_t>(stick), x, y, active == JNI_TRUE ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mobile_sotcandroid_GameActivity_nativeResetTouch(
    JNIEnv*, jobject) {
    sotcAndroidTouchReset();
}
