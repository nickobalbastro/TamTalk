#include "PluginEditor.h"
#include "BinaryData.h"

TamTalkVST3AudioProcessorEditor::TamTalkVST3AudioProcessorEditor(TamTalkVST3AudioProcessor &p)
    : AudioProcessorEditor(&p), processor(p)
{
    setSize(420, 340);

    titleLabel.setText("TamTalk VST by Nicko Balbastro v1.0.0", juce::dontSendNotification);
    titleLabel.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(titleLabel);

    appIcon = juce::ImageFileFormat::loadFrom(BinaryData::TamTalk_png, BinaryData::TamTalk_pngSize);

    portLabel.setText("Listen Port", juce::dontSendNotification);
    addAndMakeVisible(portLabel);

    portEditor.setText(juce::String(processor.getListenPort()));
    portEditor.setInputRestrictions(5, "0123456789");
    addAndMakeVisible(portEditor);

    jitterModeLabel.setText("Jitter Mode", juce::dontSendNotification);
    addAndMakeVisible(jitterModeLabel);

    jitterModeBox.addItem("Auto", 1);
    jitterModeBox.addItem("Manual", 2);
    jitterModeBox.setSelectedId(processor.isJitterAuto() ? 1 : 2, juce::dontSendNotification);
    jitterModeBox.addListener(this);
    addAndMakeVisible(jitterModeBox);

    jitterMsLabel.setText("Manual Jitter (ms)", juce::dontSendNotification);
    addAndMakeVisible(jitterMsLabel);

    jitterMsEditor.setText(juce::String(processor.getManualJitterMs()));
    jitterMsEditor.setInputRestrictions(3, "0123456789");
    jitterMsEditor.setEnabled(!processor.isJitterAuto());
    addAndMakeVisible(jitterMsEditor);

    applyButton.setButtonText("Apply");
    applyButton.addListener(this);
    addAndMakeVisible(applyButton);

    statusLabel.setText("Listening", juce::dontSendNotification);
    statusLabel.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(statusLabel);

    clientsLabel.setText("Connected Clients", juce::dontSendNotification);
    addAndMakeVisible(clientsLabel);

    clientsEditor.setReadOnly(true);
    clientsEditor.setMultiLine(true);
    clientsEditor.setScrollbarsShown(true);
    clientsEditor.setText("(none)", juce::dontSendNotification);
    addAndMakeVisible(clientsEditor);

    startTimerHz(2);
}

TamTalkVST3AudioProcessorEditor::~TamTalkVST3AudioProcessorEditor()
{
    stopTimer();
    applyButton.removeListener(this);
    jitterModeBox.removeListener(this);
}

void TamTalkVST3AudioProcessorEditor::paint(juce::Graphics &g)
{
    g.fillAll(juce::Colours::black);
    g.setColour(juce::Colours::white);
    g.drawRect(getLocalBounds(), 1);

    if (appIcon.isValid())
    {
        const auto iconBounds = getLocalBounds().removeFromTop(64).removeFromRight(64).reduced(8);
        g.drawImageWithin(appIcon, iconBounds.getX(), iconBounds.getY(), iconBounds.getWidth(), iconBounds.getHeight(), juce::RectanglePlacement::centred);
    }
}

void TamTalkVST3AudioProcessorEditor::resized()
{
    auto area = getLocalBounds().reduced(12);
    auto titleRow = area.removeFromTop(56);
    titleLabel.setBounds(titleRow.removeFromLeft(getWidth() - 96));

    auto row = area.removeFromTop(30);
    portLabel.setBounds(row.removeFromLeft(90));
    portEditor.setBounds(row.removeFromLeft(120));
    row.removeFromLeft(8);
    applyButton.setBounds(row.removeFromLeft(90));

    area.removeFromTop(8);
    row = area.removeFromTop(30);
    jitterModeLabel.setBounds(row.removeFromLeft(90));
    jitterModeBox.setBounds(row.removeFromLeft(120));

    area.removeFromTop(8);
    row = area.removeFromTop(30);
    jitterMsLabel.setBounds(row.removeFromLeft(110));
    jitterMsEditor.setBounds(row.removeFromLeft(70));

    area.removeFromTop(8);
    statusLabel.setBounds(area.removeFromTop(24));

    area.removeFromTop(8);
    clientsLabel.setBounds(area.removeFromTop(22));
    clientsEditor.setBounds(area.removeFromTop(120));
}

void TamTalkVST3AudioProcessorEditor::buttonClicked(juce::Button *button)
{
    if (button != &applyButton)
        return;

    const auto requestedPort = portEditor.getText().getIntValue();
    const auto requestedAutoMode = jitterModeBox.getSelectedId() == 1;
    const auto requestedManualJitterMs = jitterMsEditor.getText().getIntValue();

    const auto portOk = processor.setListenPort(requestedPort);
    const auto modeOk = processor.setJitterAuto(requestedAutoMode);
    const auto jitterOk = requestedAutoMode ? true : processor.setManualJitterMs(requestedManualJitterMs);

    jitterMsEditor.setEnabled(!processor.isJitterAuto());

    if (portOk && modeOk && jitterOk)
        statusLabel.setText("Applied settings", juce::dontSendNotification);
    else if (!portOk)
        statusLabel.setText("Failed to bind port", juce::dontSendNotification);
    else
        statusLabel.setText("Invalid jitter value (10-120 ms)", juce::dontSendNotification);
}

void TamTalkVST3AudioProcessorEditor::comboBoxChanged(juce::ComboBox *comboBoxThatHasChanged)
{
    if (comboBoxThatHasChanged != &jitterModeBox)
        return;

    jitterMsEditor.setEnabled(jitterModeBox.getSelectedId() != 1);
}

void TamTalkVST3AudioProcessorEditor::timerCallback()
{
    const auto ids = processor.getActiveClientIds();

    if (ids.isEmpty())
    {
        clientsEditor.setText("(none)", juce::dontSendNotification);
    }
    else
    {
        clientsEditor.setText(ids.joinIntoString("\n"), juce::dontSendNotification);
    }

    const auto modeText = processor.isJitterAuto()
                              ? "Jitter: Auto"
                              : "Jitter: Manual " + juce::String(processor.getManualJitterMs()) + "ms";
    const auto baseStatus = "Listening on UDP " + juce::String(processor.getListenPort());
    statusLabel.setText(baseStatus + " | " + modeText + " | Clients: " + juce::String(ids.size()), juce::dontSendNotification);
}
