# TamTalk VST3 (Windows)

Release version: **1.0.0**

This is a JUCE-based `VST3` plugin that listens for TamTalk LAN audio packets and outputs the mixed signal in your DAW.

## Status

- Works as MVP LAN receiver for multiple mobile clients.
- Uses `TALK` protocol from `../protocol.md`.
- Windows-focused build target (`VST3` only).

## Prerequisites

- Windows 10/11
- Visual Studio 2022 (Desktop C++)
- CMake 3.22+
- JUCE 8+ source checkout or installed JUCE CMake package

## Build

### Option A: with `JUCE_DIR`

```powershell
cd VST/vst3-plugin
cmake -S . -B build -G "Visual Studio 17 2022" -DJUCE_DIR="C:/dev/JUCE"
cmake --build build --config Release
```

### Option B: JUCE installed via CMake package

```powershell
cd VST/vst3-plugin
cmake -S . -B build -G "Visual Studio 17 2022"
cmake --build build --config Release
```

## Plugin output path

JUCE typically copies plugin to:

- `%CommonProgramFiles%/VST3/TamTalk VST.vst3`

If your DAW does not detect it, trigger plugin rescan.

## DAW / mixer routing

- Insert plugin on an audio track or aux return.
- Keep plugin track input disabled or isolated.
- Route plugin output to interface outputs feeding mixer return path.
- Keep record-input channels separate to avoid collision.

## Mobile clients

- Android sender class: `../../UI/android/AudioUdpSender.kt`
- Set `hostIp` to the PC running DAW, `port` to plugin listen port.
