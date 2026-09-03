package com.afroz.voicebubble.engine;

import com.afroz.voicebubble.speech.TtsEngine;
import com.afroz.voicebubble.speech.TtsEngine.VoiceProfile;

/**
 * Voice-system controller.
 *
 * Manages the two assistant voices (JARVIS Male default, Assistant Female),
 * the speech-rate override, and live switching without restarting. Delegates
 * the actual engine to {@link TTSManager} but adds the speech-rate application
 * and the user-name / voice-announcement helpers used by the UI.
 */
public class VoiceManager {

    private final TTSManager tts;
    private final SettingsManager settings;

    public VoiceManager(TTSManager tts, SettingsManager settings) {
        this.tts = tts;
        this.settings = settings;
    }

    public boolean isMale() {
        return settings.isMaleVoice();
    }

    /** Apply male/female profile + the configured speech rate live. */
    public void apply() {
        VoiceProfile profile = settings.isMaleVoice()
                ? VoiceProfile.JARVIS_MALE
                : VoiceProfile.ASSISTANT_FEMALE;
        tts.raw().setProfile(profile);
        float rate = settings.getSpeechRate();
        tts.raw().setRateOverride(rate);
    }

    public void setMale(boolean male) {
        settings.setMaleVoice(male);
        apply();
    }

    public void setSpeechRate(float rate) {
        settings.setSpeechRate(rate);
        apply();
    }

    public float getSpeechRate() {
        return settings.getSpeechRate();
    }

    /** Voice test — says who it is in the selected voice/language. */
    public void test(String lang) {
        String hi = settings.isMaleVoice()
                ? "नमस्ते सर, मैं जार्विस मेल वॉइस हूँ।"
                : "नमस्ते सर, मैं असिस्टेंट फीमेल वॉइस हूँ।";
        String en = settings.isMaleVoice()
                ? "Hello sir, I am the JARVIS male voice."
                : "Hello sir, I am the Assistant female voice.";
        tts.speak("hi".equals(lang) ? hi : en, "hi".equals(lang) ? "hi" : "en");
    }
}
