#if defined(__ANDROID__)
#include <android/log.h>
#include <raylib.h>
#include "runtime/host_menu.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace {
constexpr uint16_t PAD_UP = 0x0010u, PAD_DOWN = 0x0040u, PAD_LEFT = 0x0080u,
                   PAD_RIGHT = 0x0020u, PAD_START = 0x0008u, PAD_SELECT = 0x0001u,
                   PAD_CROSS = 0x4000u, PAD_CIRCLE = 0x2000u, PAD_SQUARE = 0x8000u,
                   PAD_TRIANGLE = 0x1000u, PAD_L1 = 0x0400u, PAD_L2 = 0x0100u,
                   PAD_R1 = 0x0800u, PAD_R2 = 0x0200u;
std::atomic<uint16_t> g_buttons{0};
std::atomic<float> g_stickX{0.0f}, g_stickY{0.0f};
std::atomic<bool> g_stickActive{false};

bool permittedKey(const char *s) {
    if (!s || !*s) return false;
    const bool prefix = std::strncmp(s, "PS2X_", 5) == 0 ||
                        std::strncmp(s, "SOTC_", 5) == 0 ||
                        std::strncmp(s, "CELOSIA_", 8) == 0;
    if (!prefix) return false;
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(s); *p; ++p)
        if (!( (*p >= 'A' && *p <= 'Z') || (*p >= '0' && *p <= '9') || *p == '_' ))
            return false;
    return true;
}
void trim(char *s) {
    size_t n = std::strlen(s);
    while (n && (s[n-1] == '\r' || s[n-1] == '\n' || s[n-1] == ' ' || s[n-1] == '\t'))
        s[--n] = '\0';
}
__attribute__((constructor)) void readAndroidEnvTxt() {
#ifdef PS2X_DEFAULT_BOOT_ELF
    const char *boot = PS2X_DEFAULT_BOOT_ELF;
    const char *lastSlash = std::strrchr(boot, '/');
    if (!lastSlash) return;
    char path[4096];
    const size_t dirLen = static_cast<size_t>(lastSlash - boot) + 1;
    constexpr char suffix[] = "env.txt";
    if (dirLen + sizeof(suffix) > sizeof(path)) return;
    std::memcpy(path, boot, dirLen);
    std::memcpy(path + dirLen, suffix, sizeof(suffix));
    FILE *f = std::fopen(path, "r");
    if (!f) {
        __android_log_print(ANDROID_LOG_INFO, "Celosia", "No env.txt; defaults used");
        return;
    }
    char line[2048];
    unsigned count = 0;
    while (std::fgets(line, sizeof(line), f)) {
        char *entry = line;
        while (*entry == ' ' || *entry == '\t') ++entry;
        trim(entry);
        if (!*entry || *entry == '#') continue;
        char *eq = std::strchr(entry, '=');
        if (!eq) continue;
        *eq++ = '\0';
        trim(entry);
        while (*eq == ' ' || *eq == '\t') ++eq;
        trim(eq);
        if (permittedKey(entry) && setenv(entry, eq, 1) == 0) {
            ++count;
            __android_log_print(ANDROID_LOG_INFO, "Celosia", "Loaded setting %s", entry);
        }
    }
    std::fclose(f);
    __android_log_print(ANDROID_LOG_INFO, "Celosia", "Loaded %u env.txt settings", count);
#endif
}
bool touchEnabled() {
    static const bool enabled = [] {
        const char *v = std::getenv("CELOSIA_TOUCH_CONTROLS");
        return !v || std::strcmp(v, "0") != 0;
    }();
    return enabled;
}
struct Button { float x, y, radius; uint16_t mask; const char *label; };
}

extern "C" void ps2xAndroidGetTouchState(uint16_t *buttons, float *x, float *y, bool *active) {
    if (buttons) *buttons = g_buttons.load(std::memory_order_relaxed);
    if (x) *x = g_stickX.load(std::memory_order_relaxed);
    if (y) *y = g_stickY.load(std::memory_order_relaxed);
    if (active) *active = g_stickActive.load(std::memory_order_relaxed);
}

extern "C" void ps2xAndroidDrawTouchControls() {
    auto clear = [] {
        g_buttons.store(0, std::memory_order_relaxed);
        g_stickX.store(0.0f, std::memory_order_relaxed);
        g_stickY.store(0.0f, std::memory_order_relaxed);
        g_stickActive.store(false, std::memory_order_relaxed);
    };
    if (!touchEnabled() || hostmenu::inputBlocked()) { clear(); return; }
    const float w = static_cast<float>(GetScreenWidth());
    const float h = static_cast<float>(GetScreenHeight());
    if (w < 1 || h < 1) { clear(); return; }
    const float r = std::clamp(h * 0.052f, 17.0f, 40.0f);
    const float small = r * 0.82f;
    const float sx = w * 0.145f, sy = h * 0.765f, sr = h * 0.145f;
    const std::array<Button, 10> btns{{
        {w*.10f,h*.145f,small,PAD_L2,"L2"}, {w*.22f,h*.145f,small,PAD_L1,"L1"},
        {w*.78f,h*.145f,small,PAD_R1,"R1"}, {w*.90f,h*.145f,small,PAD_R2,"R2"},
        {w*.83f,h*.785f,r,PAD_CROSS,"X"}, {w*.91f,h*.685f,r,PAD_CIRCLE,"O"},
        {w*.75f,h*.685f,r,PAD_SQUARE,"SQ"}, {w*.83f,h*.585f,r,PAD_TRIANGLE,"TRI"},
        {w*.455f,h*.915f,small,PAD_SELECT,"SEL"}, {w*.545f,h*.915f,small,PAD_START,"START"}
    }};
    uint16_t pressed = 0;
    float ax = 0, ay = 0;
    bool stick = false;
    const int count = std::clamp(GetTouchPointCount(), 0, 10);
    for (int i = 0; i < count; ++i) {
        const Vector2 p = GetTouchPosition(i);
        const float dx = p.x - sx, dy = p.y - sy;
        if (dx*dx + dy*dy < sr*sr*1.15f) {
            ax = dx / sr; ay = dy / sr;
            const float mag = std::sqrt(ax*ax + ay*ay);
            if (mag > 1) { ax /= mag; ay /= mag; }
            stick = true;
        }
        for (const Button &b : btns) {
            const float bx = p.x - b.x, by = p.y - b.y;
            if (bx*bx + by*by <= b.radius*b.radius) pressed |= b.mask;
        }
    }
    g_buttons.store(pressed, std::memory_order_relaxed);
    g_stickX.store(ax, std::memory_order_relaxed);
    g_stickY.store(ay, std::memory_order_relaxed);
    g_stickActive.store(stick, std::memory_order_relaxed);

    const Color panel{22, 29, 40, 100}, border{220, 232, 246, 165};
    const Color knob{210, 222, 238, 135};
    DrawCircle(static_cast<int>(sx), static_cast<int>(sy), sr, panel);
    DrawCircleLines(static_cast<int>(sx), static_cast<int>(sy), sr, border);
    DrawCircle(static_cast<int>(sx + ax*sr*.58f), static_cast<int>(sy + ay*sr*.58f), sr*.36f, knob);
    for (const Button &b : btns) {
        const bool down = (pressed & b.mask) != 0;
        DrawCircle(static_cast<int>(b.x), static_cast<int>(b.y), b.radius,
                   down ? Color{80, 145, 220, 190} : panel);
        DrawCircleLines(static_cast<int>(b.x), static_cast<int>(b.y), b.radius, border);
        const int fs = std::max(10, static_cast<int>(b.radius * 0.52f));
        DrawText(b.label, static_cast<int>(b.x) - MeasureText(b.label, fs)/2,
                 static_cast<int>(b.y) - fs/2, fs, RAYWHITE);
    }
}
#endif
