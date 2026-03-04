#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include <deque>
#include <map>
#include <memory>

class LanAudioReceiver : private juce::Thread
{
public:
    enum class JitterMode
    {
        autoMode = 0,
        manualMode = 1
    };

    LanAudioReceiver();
    ~LanAudioReceiver() override;

    bool startListening(int port, const juce::String &bindAddress = "0.0.0.0");
    void stopListening();

    void getMixedAudio(float *destination, int numSamples);
    juce::StringArray getActiveClientIds();
    void setJitterMode(JitterMode newMode);
    JitterMode getJitterMode() const noexcept;
    bool setManualJitterMs(int jitterMs);
    int getManualJitterMs() const noexcept;

private:
    void run() override;
    void handlePacket(const uint8_t *data, int size);
    void pruneStaleClients(uint32_t nowMs);

    struct ClientState
    {
        std::deque<float> pendingSamples;
        bool isPrimed = false;
        int adaptivePrefillSamples = 0;
        int stableBlocks = 0;
        uint32_t lastSeenMs = 0;
    };

    std::unique_ptr<juce::DatagramSocket> socket;
    int listenPort = 9240;
    JitterMode jitterMode = JitterMode::autoMode;
    int manualJitterMs = 30;

    mutable juce::CriticalSection stateLock;
    std::map<juce::String, ClientState> clients;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(LanAudioReceiver)
};
