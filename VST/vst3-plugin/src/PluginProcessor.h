#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include "LanAudioReceiver.h"

class TamTalkVST3AudioProcessor : public juce::AudioProcessor
{
public:
    TamTalkVST3AudioProcessor();
    ~TamTalkVST3AudioProcessor() override;

    void prepareToPlay(double sampleRate, int samplesPerBlock) override;
    void releaseResources() override;

    bool isBusesLayoutSupported(const BusesLayout &layouts) const override;

    void processBlock(juce::AudioBuffer<float> &, juce::MidiBuffer &) override;

    juce::AudioProcessorEditor *createEditor() override;
    bool hasEditor() const override;

    const juce::String getName() const override;

    bool acceptsMidi() const override;
    bool producesMidi() const override;
    bool isMidiEffect() const override;
    double getTailLengthSeconds() const override;

    int getNumPrograms() override;
    int getCurrentProgram() override;
    void setCurrentProgram(int index) override;
    const juce::String getProgramName(int index) override;
    void changeProgramName(int index, const juce::String &newName) override;

    void getStateInformation(juce::MemoryBlock &destData) override;
    void setStateInformation(const void *data, int sizeInBytes) override;

    int getListenPort() const noexcept;
    bool setListenPort(int newPort);
    bool setJitterAuto(bool shouldUseAuto);
    bool isJitterAuto() const noexcept;
    bool setManualJitterMs(int jitterMs);
    int getManualJitterMs() const noexcept;
    juce::StringArray getActiveClientIds();

private:
    LanAudioReceiver receiver;
    int listenPort = 9240;
    bool jitterAuto = true;
    int manualJitterMs = 30;
    float outputGain = 0.8f;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(TamTalkVST3AudioProcessor)
};
