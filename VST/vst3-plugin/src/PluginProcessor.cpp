#include "PluginProcessor.h"
#include "PluginEditor.h"

TamTalkVST3AudioProcessor::TamTalkVST3AudioProcessor()
    : AudioProcessor(BusesProperties().withOutput("Output", juce::AudioChannelSet::stereo(), true))
{
    receiver.setJitterMode(LanAudioReceiver::JitterMode::autoMode);
    receiver.setManualJitterMs(manualJitterMs);
    receiver.startListening(listenPort);
}

TamTalkVST3AudioProcessor::~TamTalkVST3AudioProcessor()
{
    receiver.stopListening();
}

const juce::String TamTalkVST3AudioProcessor::getName() const
{
    return JucePlugin_Name;
}

bool TamTalkVST3AudioProcessor::acceptsMidi() const
{
    return false;
}

bool TamTalkVST3AudioProcessor::producesMidi() const
{
    return false;
}

bool TamTalkVST3AudioProcessor::isMidiEffect() const
{
    return false;
}

double TamTalkVST3AudioProcessor::getTailLengthSeconds() const
{
    return 0.0;
}

int TamTalkVST3AudioProcessor::getNumPrograms()
{
    return 1;
}

int TamTalkVST3AudioProcessor::getCurrentProgram()
{
    return 0;
}

void TamTalkVST3AudioProcessor::setCurrentProgram(int index)
{
    juce::ignoreUnused(index);
}

const juce::String TamTalkVST3AudioProcessor::getProgramName(int index)
{
    juce::ignoreUnused(index);
    return {};
}

void TamTalkVST3AudioProcessor::changeProgramName(int index, const juce::String &newName)
{
    juce::ignoreUnused(index, newName);
}

void TamTalkVST3AudioProcessor::prepareToPlay(double sampleRate, int samplesPerBlock)
{
    juce::ignoreUnused(sampleRate, samplesPerBlock);
}

void TamTalkVST3AudioProcessor::releaseResources()
{
}

bool TamTalkVST3AudioProcessor::isBusesLayoutSupported(const BusesLayout &layouts) const
{
    const auto &outputSet = layouts.getMainOutputChannelSet();
    return outputSet == juce::AudioChannelSet::mono() || outputSet == juce::AudioChannelSet::stereo();
}

void TamTalkVST3AudioProcessor::processBlock(juce::AudioBuffer<float> &buffer, juce::MidiBuffer &midiMessages)
{
    juce::ignoreUnused(midiMessages);

    const auto numSamples = buffer.getNumSamples();
    const auto numChannels = buffer.getNumChannels();

    if (numSamples <= 0 || numChannels <= 0)
        return;

    juce::HeapBlock<float> monoMix(static_cast<size_t>(numSamples));
    receiver.getMixedAudio(monoMix.get(), numSamples);

    juce::FloatVectorOperations::multiply(monoMix.get(), outputGain, numSamples);

    for (int channel = 0; channel < numChannels; ++channel)
    {
        auto *dst = buffer.getWritePointer(channel);
        juce::FloatVectorOperations::copy(dst, monoMix.get(), numSamples);
    }
}

bool TamTalkVST3AudioProcessor::hasEditor() const
{
    return true;
}

juce::AudioProcessorEditor *TamTalkVST3AudioProcessor::createEditor()
{
    return new TamTalkVST3AudioProcessorEditor(*this);
}

void TamTalkVST3AudioProcessor::getStateInformation(juce::MemoryBlock &destData)
{
    juce::MemoryOutputStream stream(destData, true);
    stream.writeInt(listenPort);
    stream.writeBool(jitterAuto);
    stream.writeInt(manualJitterMs);
}

void TamTalkVST3AudioProcessor::setStateInformation(const void *data, int sizeInBytes)
{
    juce::MemoryInputStream stream(data, static_cast<size_t>(sizeInBytes), false);
    const auto savedPort = stream.readInt();
    if (savedPort > 0 && savedPort < 65536)
        setListenPort(savedPort);

    if (stream.getNumBytesRemaining() >= static_cast<juce::int64>(sizeof(char)))
        setJitterAuto(stream.readBool());

    if (stream.getNumBytesRemaining() >= static_cast<juce::int64>(sizeof(int)))
    {
        const auto savedJitterMs = stream.readInt();
        setManualJitterMs(savedJitterMs);
    }
}

int TamTalkVST3AudioProcessor::getListenPort() const noexcept
{
    return listenPort;
}

bool TamTalkVST3AudioProcessor::setListenPort(int newPort)
{
    if (newPort <= 0 || newPort >= 65536)
        return false;

    if (newPort == listenPort)
        return true;

    if (!receiver.startListening(newPort))
        return false;

    listenPort = newPort;
    return true;
}

bool TamTalkVST3AudioProcessor::setJitterAuto(bool shouldUseAuto)
{
    jitterAuto = shouldUseAuto;
    receiver.setJitterMode(jitterAuto ? LanAudioReceiver::JitterMode::autoMode
                                      : LanAudioReceiver::JitterMode::manualMode);
    return true;
}

bool TamTalkVST3AudioProcessor::isJitterAuto() const noexcept
{
    return jitterAuto;
}

bool TamTalkVST3AudioProcessor::setManualJitterMs(int jitterMs)
{
    if (!receiver.setManualJitterMs(jitterMs))
        return false;

    manualJitterMs = jitterMs;
    return true;
}

int TamTalkVST3AudioProcessor::getManualJitterMs() const noexcept
{
    return manualJitterMs;
}

juce::StringArray TamTalkVST3AudioProcessor::getActiveClientIds()
{
    return receiver.getActiveClientIds();
}

juce::AudioProcessor *JUCE_CALLTYPE createPluginFilter()
{
    return new TamTalkVST3AudioProcessor();
}
