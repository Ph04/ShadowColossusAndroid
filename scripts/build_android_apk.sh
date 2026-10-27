#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UPSTREAM="$ROOT/upstream/celosia"
MODE="setup"
ISO=""
JOBS="$(printenv CELOSIA_JOBS || echo 2)"

usage() {
  cat <<'EOF'
Usage:
  bash scripts/build_android_apk.sh --iso /path/to/your-own-game.iso [--jobs N]
  bash scripts/build_android_apk.sh --skip-setup

--iso         Run Celosia's source-generation pipeline using your own ISO.
--skip-setup  Reuse already generated upstream/celosia/build files.
--jobs N      Parallel native compilation jobs (default: 2).
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --iso)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      ISO="$2"
      MODE="setup"
      shift 2
      ;;
    --skip-setup)
      MODE="skip"
      shift
      ;;
    --jobs)
      [ "$#" -ge 2 ] || { usage; exit 2; }
      JOBS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [ ! -f "$UPSTREAM/celosia" ]; then
  echo "Celosia submodule is missing. Run: git submodule update --init --recursive" >&2
  exit 1
fi

if [ -z "${ANDROID_HOME:-}" ]; then
  export ANDROID_HOME="$HOME/Android/Sdk"
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/.local/gradle/gradle-8.9/bin:$PATH"
export LIBGL_ALWAYS_SOFTWARE=1

echo "==> Applying and checking Android integration"
python3 "$ROOT/scripts/apply_android_overlay.py"
python3 "$ROOT/scripts/validate_port.py"

if [ "$MODE" = "setup" ]; then
  [ -n "$ISO" ] || { echo "Pass --iso or use --skip-setup." >&2; exit 2; }
  [ -f "$ISO" ] || { echo "ISO file not found: $ISO" >&2; exit 2; }
  command -v xvfb-run >/dev/null 2>&1 || {
    echo "xvfb-run is missing. Run: bash scripts/bootstrap_codespace.sh" >&2
    exit 1
  }
  echo "==> Generating Celosia's native C++/VU1 code inside a virtual display"
  (
    cd "$UPSTREAM"
    CELOSIA_JOBS="$JOBS" xvfb-run -a -s "-screen 0 1280x720x24" \
      env LIBGL_ALWAYS_SOFTWARE=1 ./celosia setup --iso "$ISO"
  )
else
  if ! compgen -G "$UPSTREAM/build/runner/*.cpp" >/dev/null; then
    echo "No generated runner C++ files found. Run with --iso first." >&2
    exit 1
  fi
  if [ ! -d "$UPSTREAM/build/vu1rec" ] || [ ! -d "$UPSTREAM/game/overrides" ]; then
    echo "Generated VU1 sources or game overrides are missing. Run with --iso first." >&2
    exit 1
  fi
fi

ANDROID_PROJECT="$UPSTREAM/PS2Recomp/android"
cd "$ANDROID_PROJECT"

if [ ! -x ./gradlew ]; then
  command -v gradle >/dev/null 2>&1 || {
    echo "Gradle 8.9 missing. Run: bash scripts/bootstrap_codespace.sh" >&2
    exit 1
  }
  gradle wrapper --gradle-version 8.9
fi

echo "==> Building Android APK (arm64-v8a)"
./gradlew --no-daemon assembleDebug

APK="$ANDROID_PROJECT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "Gradle finished without the expected APK: $APK" >&2; exit 1; }
echo
echo "BUILD SUCCESS: $APK"
sha256sum "$APK"
