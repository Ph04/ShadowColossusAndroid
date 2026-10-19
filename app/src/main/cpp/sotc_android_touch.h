#pragma once

#include <cstdint>

extern "C" {
void sotcAndroidTouchSetButton(uint32_t buttonMask, int pressed);
void sotcAndroidTouchSetStick(uint32_t stick, float x, float y, int active);
void sotcAndroidTouchReset();
void sotcAndroidTouchRead(uint16_t* buttons,
                          float* leftX, float* leftY,
                          float* rightX, float* rightY,
                          bool* leftActive, bool* rightActive);
}
