cmake_minimum_required(VERSION 3.22)
project(TamTalkVST3 VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT WIN32)
    message(FATAL_ERROR "TamTalkVST3 is currently configured for Windows builds only.")
endif()

# Set this when configuring if JUCE is not globally available:
# cmake -B build -S . -DJUCE_DIR=C:/dev/JUCE
if (JUCE_DIR)
    add_subdirectory(${JUCE_DIR} JUCE)
else()
    find_package(JUCE CONFIG REQUIRED)
endif()

juce_add_plugin(TamTalkVST3
    COMPANY_NAME "TamTalk"
    IS_SYNTH FALSE
    NEEDS_MIDI_INPUT FALSE
    NEEDS_MIDI_OUTPUT FALSE
    IS_MIDI_EFFECT FALSE
    EDITOR_WANTS_KEYBOARD_FOCUS FALSE
    COPY_PLUGIN_AFTER_BUILD TRUE
    PLUGIN_MANUFACTURER_CODE TmTk
    PLUGIN_CODE TmV3
    FORMATS VST3
    PRODUCT_NAME "TamTalk VST"
)

target_sources(TamTalkVST3 PRIVATE
    src/PluginProcessor.cpp
    src/PluginProcessor.h
    src/PluginEditor.cpp
    src/PluginEditor.h
    src/LanAudioReceiver.cpp
    src/LanAudioReceiver.h
)

juce_add_binary_data(TamTalkAssets
    SOURCES
        assets/TamTalk.png
)

target_compile_definitions(TamTalkVST3
    PUBLIC
        JUCE_WEB_BROWSER=0
        JUCE_USE_CURL=0
    JUCE_VST3_CAN_REPLACE_VST2=0
)

target_link_libraries(TamTalkVST3
    PRIVATE
        juce::juce_audio_utils
        TamTalkAssets
    PUBLIC
        juce::juce_recommended_config_flags
        juce::juce_recommended_lto_flags
        juce::juce_recommended_warning_flags
)
