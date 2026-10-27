from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
checks = {
    "upstream submodule": root / "upstream" / "celosia" / "PS2Recomp" / "CMakeLists.txt",
    "Gradle overlay": root / "android-overlay" / "PS2Recomp" / "android" / "app" / "build.gradle",
    "runtime helper": root / "android-overlay" / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_android_runtime.cpp",
    "overlay application script": root / "scripts" / "apply_android_overlay.py",
}
failed = False
for label, path in checks.items():
    ok = path.is_file()
    print(("OK   " if ok else "FAIL ") + label)
    failed |= not ok
if failed:
    sys.exit(1)
print("Static layout audit passed; no NDK build or gameplay test was run.")
