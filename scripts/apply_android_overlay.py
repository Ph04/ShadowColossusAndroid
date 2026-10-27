#!/usr/bin/env python3
"""Install the Celosia Android overlay into the pinned upstream submodule."""
from pathlib import Path
import argparse
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / "upstream" / "celosia"
OVERLAY = ROOT / "android-overlay"

def read(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"required file not found: {path}")
    return path.read_text(encoding="utf-8")

def add_after(path: Path, anchor: str, insertion: str, marker: str, check_only: bool) -> None:
    text = read(path)
    if marker in text:
        print(f"already integrated: {path.relative_to(ROOT)}")
        return
    if text.count(anchor) != 1:
        raise RuntimeError(f"expected exactly one anchor in {path}: {anchor!r}")
    if check_only:
        print(f"patchable: {path.relative_to(ROOT)}")
        return
    path.write_text(text.replace(anchor, anchor + insertion, 1), encoding="utf-8")

def add_before(path: Path, anchor: str, insertion: str, marker: str, check_only: bool) -> None:
    text = read(path)
    if marker in text:
        print(f"already integrated: {path.relative_to(ROOT)}")
        return
    if text.count(anchor) != 1:
        raise RuntimeError(f"expected exactly one anchor in {path}: {anchor!r}")
    if check_only:
        print(f"patchable: {path.relative_to(ROOT)}")
        return
    path.write_text(text.replace(anchor, insertion + anchor, 1), encoding="utf-8")

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    if not (UPSTREAM / "PS2Recomp" / "CMakeLists.txt").is_file():
        raise RuntimeError("upstream submodule missing; run git submodule update --init --recursive")

    if not args.check_only:
        source = OVERLAY / "PS2Recomp" / "android"
        dest = UPSTREAM / "PS2Recomp" / "android"
        if not source.is_dir():
            raise RuntimeError(f"Android overlay missing: {source}")
        shutil.copytree(source, dest, dirs_exist_ok=True)
        runtime_overlay = OVERLAY / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_android_runtime.cpp"
        runtime_dest = UPSTREAM / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_android_runtime.cpp"
        runtime_dest.write_text(read(runtime_overlay), encoding="utf-8")
        print("installed Gradle files and Android runtime helper")

    pad = UPSTREAM / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_pad.cpp"
    runtime = UPSTREAM / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_runtime.cpp"
    pad_decl = (
        '\n#if defined(__ANDROID__)\n'
        'extern "C" void ps2xAndroidGetTouchState(uint16_t *buttons, float *x, float *y, bool *active);\n'
        '#endif\n'
    )
    add_after(pad, '#include <cstring>\n', pad_decl,
              'extern "C" void ps2xAndroidGetTouchState(', args.check_only)

    pad_block = r'''
#if defined(__ANDROID__)
    // Merge virtual touch input with physical input. Pad bits are active-low.
    {
        uint16_t touchButtons = 0;
        float touchX = 0.0f, touchY = 0.0f;
        bool touchActive = false;
        ps2xAndroidGetTouchState(&touchButtons, &touchX, &touchY, &touchActive);
        if (touchButtons & PAD_UP) clearBit(PAD_UP);
        if (touchButtons & PAD_DOWN) clearBit(PAD_DOWN);
        if (touchButtons & PAD_LEFT) clearBit(PAD_LEFT);
        if (touchButtons & PAD_RIGHT) clearBit(PAD_RIGHT);
        if (touchButtons & PAD_CROSS) clearBit(PAD_CROSS);
        if (touchButtons & PAD_CIRCLE) clearBit(PAD_CIRCLE);
        if (touchButtons & PAD_SQUARE) clearBit(PAD_SQUARE);
        if (touchButtons & PAD_TRIANGLE) clearBit(PAD_TRIANGLE);
        if (touchButtons & PAD_L1) clearBit(PAD_L1);
        if (touchButtons & PAD_L2) clearBit(PAD_L2);
        if (touchButtons & PAD_R1) clearBit(PAD_R1);
        if (touchButtons & PAD_R2) clearBit(PAD_R2);
        if (touchButtons & PAD_START) clearBit(PAD_START);
        if (touchButtons & PAD_SELECT) clearBit(PAD_SELECT);
        if (touchActive) {
            data[6] = static_cast<uint8_t>(std::clamp(128.0f + touchX * 127.5f, 0.0f, 255.0f));
            data[7] = static_cast<uint8_t>(std::clamp(128.0f + touchY * 127.5f, 0.0f, 255.0f));
        }
    }
#endif

'''
    add_before(pad, '    // PS2X_INPUT_SCRIPT=', pad_block,
               'Merge virtual touch input', args.check_only)

    runtime_decl = (
        '\n#if defined(__ANDROID__)\n'
        'extern "C" void ps2xAndroidDrawTouchControls();\n'
        '#endif\n'
    )
    add_after(runtime, '#include "ps2x/iop/audio_stats.h"\n', runtime_decl,
              'extern "C" void ps2xAndroidDrawTouchControls();', args.check_only)
    draw_call = (
        '\n#if defined(__ANDROID__)\n'
        '            ps2xAndroidDrawTouchControls();\n'
        '#endif'
    )
    add_after(runtime, '            hostmenu::draw(GetScreenWidth(), GetScreenHeight());\n',
              draw_call, 'ps2xAndroidDrawTouchControls();', args.check_only)
    print("Android overlay audit/application complete.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
