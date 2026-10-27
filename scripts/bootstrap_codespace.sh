#!/usr/bin/env bash
set -euo pipefail

# Reproducible cloud workstation for a phone-only workflow (GitHub Codespaces).
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Initializing the pinned Celosia source"
git submodule update --init --recursive

echo "==> Installing Linux host dependencies"
sudo apt-get update
sudo apt-get install -y --no-install-recommends \
  build-essential cmake ninja-build pkg-config python3 python3-pip \
  libarchive-tools libavcodec-dev libavformat-dev libavutil-dev \
  libswresample-dev libswscale-dev ffmpeg \
  xvfb xauth mesa-utils libgl1-mesa-dri libgl1-mesa-dev \
  libx11-dev libxext-dev libxrandr-dev libxinerama-dev libxcursor-dev libxi-dev \
  libasound2-dev curl unzip zip openjdk-17-jdk-headless

SDK_ROOT="$HOME/Android/Sdk"
mkdir -p "$SDK_ROOT/cmdline-tools" "$HOME/.local/gradle"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Downloading Android SDK command-line tools"
  TEMP_DIR="$(mktemp -d)"
  curl -fL --retry 3 \
    "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" \
    -o "$TEMP_DIR/android-cmdline-tools.zip"
  unzip -q "$TEMP_DIR/android-cmdline-tools.zip" -d "$TEMP_DIR/unpacked"
  mkdir -p "$SDK_ROOT/cmdline-tools/latest"
  cp -a "$TEMP_DIR/unpacked/cmdline-tools/." "$SDK_ROOT/cmdline-tools/latest/"
  rm -rf "$TEMP_DIR"
fi

if [ ! -x "$HOME/.local/gradle/gradle-8.9/bin/gradle" ]; then
  echo "==> Downloading Gradle 8.9"
  TEMP_DIR="$(mktemp -d)"
  curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-8.9-bin.zip" \
    -o "$TEMP_DIR/gradle.zip"
  unzip -q "$TEMP_DIR/gradle.zip" -d "$HOME/.local/gradle"
  rm -rf "$TEMP_DIR"
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$HOME/.local/gradle/gradle-8.9/bin:$PATH"

# Persist the tool paths for subsequent Codespaces terminal sessions.
if ! grep -q '# Celosia Android build environment' "$HOME/.bashrc" 2>/dev/null; then
  cat >> "$HOME/.bashrc" <<'EOF'

# Celosia Android build environment
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/.local/gradle/gradle-8.9/bin:$PATH"
export LIBGL_ALWAYS_SOFTWARE=1
EOF
fi

echo "==> Accepting Android SDK licences and installing pinned packages"
set +o pipefail
yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses >/dev/null
set -o pipefail
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "ndk;28.2.13676358" \
  "cmake;3.22.1"

echo
echo "Cloud build environment prepared."
echo "Next: upload your own game ISO into this Codespace (do not commit it), then run:"
echo "  bash scripts/build_android_apk.sh --iso '/workspaces/ShadowColossusAndroid/your-game.iso'"
