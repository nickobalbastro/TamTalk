#include "LanAudioReceiver.h"

#include <cmath>

namespace
{
    constexpr uint8_t protocolVersion = 1;
    constexpr uint8_t msgAudio = 1;
    constexpr uint32_t clientTimeoutMs = 2500;
    constexpr int sampleRateHz = 48000;
    constexpr int jitterPrefillMs = 30;
    constexpr int jitterMinMs = 10;
    constexpr int maxJitterBufferMs = 120;
    constexpr int jitterPrefillSamples = (sampleRateHz * jitterPrefillMs) / 1000;
    constexpr int jitterMinSamples = (sampleRateHz * jitterMinMs) / 1000;
    constexpr int maxJitterBufferSamples = (sampleRateHz * maxJitterBufferMs) / 1000;
    constexpr int autoAdjustStepMs = 5;
    constexpr int autoAdjustStepSamples = (sampleRateHz * autoAdjustStepMs) / 1000;
    constexpr int stableBlocksBeforeReduce = 40;
    constexpr float limiterDrive = 1.5f;
    const float limiterNorm = 1.0f / std::tanh(limiterDrive);
}

LanAudioReceiver::LanAudioReceiver()
    : Thread("LanAudioReceiver")
{
}

LanAudioReceiver::~LanAudioReceiver()
{
    stopListening();
}

bool LanAudioReceiver::startListening(int port, const juce::String &bindAddress)
{
    stopListening();

    auto newSocket = std::make_unique<juce::DatagramSocket>(false);
    newSocket->setEnablePortReuse(true);

    listenPort = port;
    if (!newSocket->bindToPort(listenPort, bindAddress))
        return false;

    {
        const juce::ScopedLock lock(stateLock);
        socket = std::move(newSocket);
    }

    startThread();
    return true;
}

void LanAudioReceiver::stopListening()
{
    signalThreadShouldExit();

    {
        const juce::ScopedLock lock(stateLock);
        if (socket != nullptr)
            socket->shutdown();
    }

    stopThread(1000);

    {
        const juce::ScopedLock lock(stateLock);
        socket.reset();
        clients.clear();
    }
}

void LanAudioReceiver::getMixedAudio(float *destination, int numSamples)
{
    juce::FloatVectorOperations::clear(destination, numSamples);

    const auto nowMs = juce::Time::getMillisecondCounter();

    const juce::ScopedLock lock(stateLock);
    pruneStaleClients(nowMs);

    int activeClients = 0;

    for (auto &[clientId, client] : clients)
    {
        juce::ignoreUnused(clientId);

        const auto prefillSamples = jitterMode == JitterMode::manualMode
                                        ? (sampleRateHz * juce::jlimit(jitterMinMs, maxJitterBufferMs, manualJitterMs)) / 1000
                                        : juce::jlimit(jitterMinSamples, maxJitterBufferSamples, client.adaptivePrefillSamples);

        if (!client.isPrimed)
        {
            if (static_cast<int>(client.pendingSamples.size()) < prefillSamples)
                continue;

            client.isPrimed = true;
        }

        const auto copyCount = juce::jmin(numSamples, static_cast<int>(client.pendingSamples.size()));
        if (copyCount <= 0)
        {
            client.isPrimed = false;
            continue;
        }

        ++activeClients;

        for (int i = 0; i < copyCount; ++i)
        {
            destination[i] += client.pendingSamples.front();
            client.pendingSamples.pop_front();
        }

        const auto underrun = copyCount < numSamples;

        if (underrun || client.pendingSamples.empty())
            client.isPrimed = false;

        if (jitterMode == JitterMode::autoMode)
        {
            if (underrun)
            {
                client.adaptivePrefillSamples = juce::jmin(maxJitterBufferSamples, client.adaptivePrefillSamples + autoAdjustStepSamples);
                client.stableBlocks = 0;
            }
            else
            {
                ++client.stableBlocks;
                if (client.stableBlocks >= stableBlocksBeforeReduce && client.adaptivePrefillSamples > jitterMinSamples)
                {
                    client.adaptivePrefillSamples = juce::jmax(jitterMinSamples, client.adaptivePrefillSamples - autoAdjustStepSamples);
                    client.stableBlocks = 0;
                }
            }
        }
    }

    if (activeClients <= 0)
        return;

    const auto headroom = 1.0f / std::sqrt(static_cast<float>(activeClients));
    juce::FloatVectorOperations::multiply(destination, headroom, numSamples);

    for (int i = 0; i < numSamples; ++i)
    {
        const auto sample = destination[i] * limiterDrive;
        destination[i] = std::tanh(sample) * limiterNorm;
    }
}

juce::StringArray LanAudioReceiver::getActiveClientIds()
{
    juce::StringArray ids;
    const auto nowMs = juce::Time::getMillisecondCounter();

    const juce::ScopedLock lock(stateLock);
    pruneStaleClients(nowMs);

    for (const auto &[clientId, _client] : clients)
    {
        juce::ignoreUnused(_client);
        ids.add(clientId);
    }

    return ids;
}

void LanAudioReceiver::setJitterMode(JitterMode newMode)
{
    const juce::ScopedLock lock(stateLock);
    jitterMode = newMode;
    for (auto &[clientId, client] : clients)
    {
        juce::ignoreUnused(clientId);
        client.isPrimed = false;
        client.stableBlocks = 0;
        client.adaptivePrefillSamples = jitterPrefillSamples;
    }
}

LanAudioReceiver::JitterMode LanAudioReceiver::getJitterMode() const noexcept
{
    const juce::ScopedLock lock(stateLock);
    return jitterMode;
}

bool LanAudioReceiver::setManualJitterMs(int jitterMs)
{
    if (jitterMs < jitterMinMs || jitterMs > maxJitterBufferMs)
        return false;

    const juce::ScopedLock lock(stateLock);
    manualJitterMs = jitterMs;
    for (auto &[clientId, client] : clients)
    {
        juce::ignoreUnused(clientId);
        client.isPrimed = false;
    }

    return true;
}

int LanAudioReceiver::getManualJitterMs() const noexcept
{
    const juce::ScopedLock lock(stateLock);
    return manualJitterMs;
}

void LanAudioReceiver::run()
{
    juce::HeapBlock<uint8_t> packet(4096);

    while (!threadShouldExit())
    {
        juce::DatagramSocket *activeSocket = nullptr;
        {
            const juce::ScopedLock lock(stateLock);
            activeSocket = socket.get();
        }

        if (activeSocket == nullptr)
        {
            wait(10);
            continue;
        }

        if (activeSocket->waitUntilReady(true, 200) <= 0)
            continue;

        const auto bytes = activeSocket->read(packet.get(), 4096, false);
        if (bytes > 0)
            handlePacket(packet.get(), bytes);
    }
}

void LanAudioReceiver::handlePacket(const uint8_t *data, int size)
{
    if (size < 20)
        return;

    if (!(data[0] == 'T' && data[1] == 'A' && data[2] == 'L' && data[3] == 'K'))
        return;

    const auto version = data[4];
    const auto msgType = data[5];
    if (version != protocolVersion)
        return;

    const auto clientLen = juce::ByteOrder::littleEndianShort(data + 18);
    const auto headerSize = 20 + static_cast<int>(clientLen);
    if (headerSize > size)
        return;

    const auto clientId = juce::String::fromUTF8(reinterpret_cast<const char *>(data + 20), clientLen);
    if (clientId.isEmpty())
        return;

    const auto nowMs = juce::Time::getMillisecondCounter();

    if (msgType != msgAudio)
    {
        const juce::ScopedLock lock(stateLock);
        clients[clientId].lastSeenMs = nowMs;
        return;
    }

    const auto payloadBytes = size - headerSize;
    if (payloadBytes <= 0 || (payloadBytes % 2) != 0)
        return;

    const auto sampleCount = payloadBytes / 2;

    auto *pcm = reinterpret_cast<const int16_t *>(data + headerSize);

    const juce::ScopedLock lock(stateLock);
    auto &client = clients[clientId];

    if (client.adaptivePrefillSamples <= 0)
        client.adaptivePrefillSamples = jitterPrefillSamples;

    for (int i = 0; i < sampleCount; ++i)
    {
        const auto sample = static_cast<float>(juce::ByteOrder::swapIfBigEndian(pcm[i])) / 32768.0f;
        client.pendingSamples.push_back(sample);
    }

    while (static_cast<int>(client.pendingSamples.size()) > maxJitterBufferSamples)
        client.pendingSamples.pop_front();

    client.lastSeenMs = nowMs;
}

void LanAudioReceiver::pruneStaleClients(uint32_t nowMs)
{
    for (auto it = clients.begin(); it != clients.end();)
    {
        if ((nowMs - it->second.lastSeenMs) > clientTimeoutMs)
            it = clients.erase(it);
        else
            ++it;
    }
}
