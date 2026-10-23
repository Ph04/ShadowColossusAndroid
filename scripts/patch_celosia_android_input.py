#!/usr/bin/env python3
"""Apply the touch-overlay bridge to Celosia's PS2 pad poller, idempotently."""
from __future__ import annotations

import argparse
from pathlib import Path

INCLUDE_ANCHOR = '#include "ps2_host_backend.h"'
INCLUDE_BLOCK = '''#include "ps2_host_backend.h"
#if defined(__ANDROID__)
#include <initializer_list>
#include "sotc_android_touch.h"
#endif'''

PAD_ANCHOR = '    data[2] = static_cast<uint8_t>(btns & 0xFF);'
PAD_BLOCK = '''#if defined(__ANDROID__)
    // Merge the Android overlay with any gamepad/keyboard input. PS2 pad bits
    // are active-low in this backend, while the overlay stores pressed bits.
    uint16_t touchButtons = 0u;
    float touchLeftX = 0.0f, touchLeftY = 0.0f;
    float touchRightX = 0.0f, touchRightY = 0.0f;
    bool touchLeftActive = false, touchRightActive = false;
    sotcAndroidTouchRead(&touchButtons,
                         &touchLeftX, &touchLeftY,
                         &touchRightX, &touchRightY,
                         &touchLeftActive, &touchRightActive);
    for (const uint16_t mask : {PAD_SELECT, PAD_L3, PAD_R3, PAD_START,
                                PAD_UP, PAD_RIGHT, PAD_DOWN, PAD_LEFT,
                                PAD_L2, PAD_R2, PAD_L1, PAD_R1,
                                PAD_TRIANGLE, PAD_CIRCLE, PAD_CROSS, PAD_SQUARE})
    {
        if ((touchButtons & mask) != 0u)
            clearBit(mask);
    }
    auto touchToByte = [](float value) {
        return static_cast<uint8_t>(std::clamp(128.0f + value * 127.5f, 0.0f, 255.0f));
    };
    if (touchLeftActive)
    {
        data[6] = touchToByte(touchLeftX);
        data[7] = touchToByte(touchLeftY);
    }
    if (touchRightActive)
    {
        data[4] = touchToByte(touchRightX);
        data[5] = touchToByte(touchRightY);
    }
#endif
    data[2] = static_cast<uint8_t>(btns & 0xFF);'''


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if "sotcAndroidTouchRead" in text and "sotc_android_touch.h" in text:
        if text.count("sotcAndroidTouchRead") != 1:
            raise SystemExit(f"Hook touch duplicado; revise {source}.")
        print(f"Celosia Android touch hook already applied: {source}")
        return
    if INCLUDE_ANCHOR not in text:
        raise SystemExit(f"Não reconheci o include esperado em {source}; nenhum arquivo foi gravado.")
    if PAD_ANCHOR not in text:
        raise SystemExit(f"Não reconheci o ponto de leitura do controle em {source}; nenhum arquivo foi gravado.")
    text = text.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1)
    text = text.replace(PAD_ANCHOR, PAD_BLOCK, 1)
    source.write_text(text, encoding="utf-8")
    print(f"Celosia Android touch hook aplicado: {source}")


def self_test() -> None:
    sample = INCLUDE_ANCHOR + "\n" + PAD_ANCHOR
    result = sample.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1).replace(PAD_ANCHOR, PAD_BLOCK, 1)
    assert result.count("sotcAndroidTouchRead") == 1
    assert result.count("sotc_android_touch.h") == 1
    assert result.index("#if defined(__ANDROID__)") < result.index("sotcAndroidTouchRead")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="path to Celosia ps2_pad.cpp")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Celosia Android touch patch self-test passed.")
        return 0
    if not args.source:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit(f"Arquivo Celosia não encontrado: {args.source}")
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
