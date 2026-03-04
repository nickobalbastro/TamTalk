@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64
"C:\Program Files\CMake\bin\cmake.exe" -S VST\vst3-plugin -B VST\vst3-plugin\build-nmake -G "NMake Makefiles" -DJUCE_DIR="C:/Users/nicko/Documents/TamTalk/VST/JUCE"
"C:\Program Files\CMake\bin\cmake.exe" --build VST\vst3-plugin\build-nmake --config Release

if not exist release\vst mkdir release\vst

if exist "VST\vst3-plugin\build-nmake\TamTalkVST3_artefacts\Release\VST3\TamTalk LAN Receiver.vst3" (
	xcopy /E /I /Y "VST\vst3-plugin\build-nmake\TamTalkVST3_artefacts\Release\VST3\TamTalk LAN Receiver.vst3" "release\vst\TamTalk LAN Receiver.vst3\" >nul
) else if exist "VST\vst3-plugin\build-nmake\TamTalkVST3_artefacts\Debug\VST3\TamTalk LAN Receiver.vst3" (
	xcopy /E /I /Y "VST\vst3-plugin\build-nmake\TamTalkVST3_artefacts\Debug\VST3\TamTalk LAN Receiver.vst3" "release\vst\TamTalk LAN Receiver.vst3\" >nul
) else (
	echo Warning: VST3 artifact not found under build-nmake artefacts.
)
