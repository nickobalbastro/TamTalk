#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-all}"
CONFIG="${2:-Release}"
RELEASE_DIR="$ROOT_DIR/release"

mkdir -p "$RELEASE_DIR"

build_ui() {
  echo "[TamTalk] Building UI (Android debug APK)..."
  cd "$ROOT_DIR/UI/moble/android"

  if [[ -x "./gradlew" ]]; then
    ./gradlew :app:assembleDebug
  else
    ./gradlew.bat :app:assembleDebug
  fi

  local apk_src="$ROOT_DIR/UI/moble/android/app/build/outputs/apk/debug/app-debug.apk"
  local ui_release_dir="$RELEASE_DIR/ui"
  mkdir -p "$ui_release_dir"

  if [[ -f "$apk_src" ]]; then
    cp "$apk_src" "$ui_release_dir/tamtalk-android-debug.apk"
    echo "[TamTalk] UI artifact copied: release/ui/tamtalk-android-debug.apk"
  else
    echo "[TamTalk] Warning: APK not found at expected path: $apk_src"
  fi

  echo "[TamTalk] UI build complete: UI/moble/android/app/build/outputs/apk/debug/app-debug.apk"
}

build_vst() {
  echo "[TamTalk] Building VST3 ($CONFIG)..."
  local vst_dir="$ROOT_DIR/VST/vst3-plugin"
  local build_dir="$vst_dir/build"

  mkdir -p "$build_dir"

  local -a cmake_args
  cmake_args=(-S "$vst_dir" -B "$build_dir")

  if [[ -n "${CMAKE_GENERATOR:-}" ]]; then
    cmake_args+=( -G "$CMAKE_GENERATOR" )
  fi

  if [[ -n "${JUCE_DIR:-}" ]]; then
    cmake_args+=( -DJUCE_DIR="$JUCE_DIR" )
  fi

  cmake "${cmake_args[@]}"
  cmake --build "$build_dir" --config "$CONFIG"

  local vst_release_dir="$RELEASE_DIR/vst"
  mkdir -p "$vst_release_dir"

  mapfile -t vst_artifacts < <(find "$build_dir" -type d -name "*.vst3" 2>/dev/null)
  if [[ ${#vst_artifacts[@]} -eq 0 ]]; then
    mapfile -t vst_artifacts < <(find "$ROOT_DIR" -type d -name "TamTalk LAN Receiver.vst3" 2>/dev/null)
  fi

  if [[ ${#vst_artifacts[@]} -gt 0 ]]; then
    for artifact in "${vst_artifacts[@]}"; do
      local name
      name="$(basename "$artifact")"
      rm -rf "$vst_release_dir/$name"
      cp -R "$artifact" "$vst_release_dir/$name"
      echo "[TamTalk] VST artifact copied: release/vst/$name"
    done
  else
    echo "[TamTalk] Warning: no .vst3 artifact found under $build_dir"
  fi

  echo "[TamTalk] VST build complete. Check: VST/vst3-plugin/build"
}

case "$TARGET" in
  ui)
    build_ui
    ;;
  vst)
    build_vst
    ;;
  all)
    build_ui
    build_vst
    ;;
  *)
    echo "Usage: ./build.sh [ui|vst|all] [Debug|Release]"
    echo "Examples:"
    echo "  ./build.sh ui"
    echo "  ./build.sh vst Release"
    echo "  JUCE_DIR=/c/dev/JUCE CMAKE_GENERATOR='Visual Studio 17 2022' ./build.sh vst Release"
    exit 1
    ;;
esac
