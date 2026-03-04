#pragma once

#include <juce_gui_extra/juce_gui_extra.h>
#include "PluginProcessor.h"

class TamTalkVST3AudioProcessorEditor : public juce::AudioProcessorEditor,
                                        private juce::Button::Listener,
                                        private juce::ComboBox::Listener,
                                        private juce::Timer
{
public:
    explicit TamTalkVST3AudioProcessorEditor(TamTalkVST3AudioProcessor &);
    ~TamTalkVST3AudioProcessorEditor() override;

    void paint(juce::Graphics &) override;
    void resized() override;

private:
    void buttonClicked(juce::Button *button) override;
    void comboBoxChanged(juce::ComboBox *comboBoxThatHasChanged) override;
    void timerCallback() override;

    TamTalkVST3AudioProcessor &processor;

    juce::Label titleLabel;
    juce::Label portLabel;
    juce::TextEditor portEditor;
    juce::Label jitterModeLabel;
    juce::ComboBox jitterModeBox;
    juce::Label jitterMsLabel;
    juce::TextEditor jitterMsEditor;
    juce::TextButton applyButton;
    juce::Label statusLabel;
    juce::Label clientsLabel;
    juce::TextEditor clientsEditor;
    juce::Image appIcon;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(TamTalkVST3AudioProcessorEditor)
};
