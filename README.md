# TamTalk

TamTalk is an Android-to-VST3 LAN voice streaming tool. It sends microphone audio from Android phones directly into the TamTalk VST plugin in your DAW/mixer.

No phone-side audio driver is required, so it won’t conflict with dedicated ASIO setups.

## Release binaries

Prebuilt binaries are in the `release` folder:

- `release/ui/tamtalk-android-debug.apk`
- `release/vst/*.vst3`

Official releases: https://github.com/NickoCB/TamTalk/releases

## Default connection

- UDP port: `9240`
- Android app → TamTalk VST on the same LAN
