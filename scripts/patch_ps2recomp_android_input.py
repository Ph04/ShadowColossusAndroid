#!/usr/bin/env python3
"""Apply the Android touchpad hook to the pinned PS2Recomp checkout.

The upstream submodule remains pinned and unmodified in Git. This patch is
applied only to the local build tree, so generated/game data is never uploaded.
"""
from __future__ import annotations

import argparse
from pathlib import Path

INCLUDE_ANCHOR = '#include "ps2_host_backend.h"'
INCLUDE_BLOCK = '''#include "ps2_host_backend.h"
#if defined(__ANDROID__)
#include "sotc_android_touch.h"
#endif'''

READ_ANCHOR = '''            applyPressure(state);
            return state;
        }
    }

    void setRightStickInvertDefault'''
READ_BLOCK = '''#if defined(__ANDROID__)
            if (port == 0u)
            {
                uint16_t touchButtons = 0u;
                float touchLeftX = 0.0f, touchLeftY = 0.0f;
                float touchRightX = 0.0f, touchRightY = 0.0f;
                bool touchLeftActive = false, touchRightActive = false;
                sotcAndroidTouchRead(&touchButtons,
                                     &touchLeftX, &touchLeftY,
                                     &touchRightX, &touchRightY,
                                     &touchLeftActive, &touchRightActive);
                state.buttons |= touchButtons;
                if (touchLeftActive)
                {
                    state.leftX = axisToByte(touchLeftX);
                    state.leftY = axisToByte(touchLeftY);
                }
                if (touchRightActive)
                {
                    state.rightX = axisToByte((rightStickInvert() & kInvertRightX) != 0u ? -touchRightX : touchRightX);
                    state.rightY = axisToByte((rightStickInvert() & kInvertRightY) != 0u ? -touchRightY : touchRightY);
                }
            }
#endif
            applyPressure(state);
            return state;
        }
    }

    void setRightStickInvertDefault'''


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if "sotcAndroidTouchRead" in text and "sotc_android_touch.h" in text:
        # Allow a local checkout patched by an earlier app build to pick up
        # correctness fixes without requiring the user to reclone submodules.
        text = text.replace(
            "state.rightX = axisToByte(touchRightX);",
            "state.rightX = axisToByte((rightStickInvert() & kInvertRightX) != 0u ? -touchRightX : touchRightX);",
        ).replace(
            "state.rightY = axisToByte(touchRightY);",
            "state.rightY = axisToByte((rightStickInvert() & kInvertRightY) != 0u ? -touchRightY : touchRightY);",
        )
        source.write_text(text, encoding="utf-8")
        print(f"Android touch hook already applied / refreshed: {source}")
        return
    if INCLUDE_ANCHOR not in text:
        raise SystemExit(f"Não reconheci o cabeçalho esperado em {source}; PS2Recomp pode ter mudado.")
    text = text.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1)
    if READ_ANCHOR not in text:
        raise SystemExit(f"Não encontrei o ponto de leitura do controle em {source}; nada foi gravado.")
    text = text.replace(READ_ANCHOR, READ_BLOCK, 1)
    source.write_text(text, encoding="utf-8")
    print(f"Android touch hook aplicado a: {source}")


def self_test() -> None:
    sample = '''#include "ps2_host_backend.h"
            applyPressure(state);
            return state;
        }
    }

    void setRightStickInvertDefault'''
    assert INCLUDE_ANCHOR in sample and READ_ANCHOR in sample
    result = sample.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1).replace(READ_ANCHOR, READ_BLOCK, 1)
    assert result.count("sotcAndroidTouchRead") == 1
    assert result.count("sotc_android_touch.h") == 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="path to PS2Recomp ps2_host_input.cpp")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Patch self-test passed.")
        return 0
    if not args.source:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit(f"Arquivo PS2Recomp não encontrado: {args.source}")
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
