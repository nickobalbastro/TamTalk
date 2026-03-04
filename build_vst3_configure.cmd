@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64
"C:\Program Files\CMake\bin\cmake.exe" -S VST\vst3-plugin -B VST\vst3-plugin\build-nmake -G "NMake Makefiles" -DJUCE_DIR="C:/Users/nicko/Documents/TamTalk/VST/JUCE"
