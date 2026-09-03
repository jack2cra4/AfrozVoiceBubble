package com.afroz.voicebubble.engine;

import android.content.Context;

import com.afroz.voicebubble.speech.TtsEngine;
import com.afroz.voicebubble.speech.TtsEngine.VoiceProfile;

/**
 * TTS facade used by the whole pipeline.
 *
 * Responsibilities:
 *  - Resolve output language from the selected {@link LocalMemory.LanguageMode}
 *    (HINDI / ENGLISH / AUTO) plus the detected input language.
 *  - Central, interruptible speak path (TTS is stopped before any new
 *    utterance, so old responses never talk over a new request).
 *  - Apply the configured voice profile (JARVIS Male / Assistant Female)
 *    without restarting the engine.
 *  - Honour the global mute setting.
 */
public class TTSManager {

    private final TtsEngine engine;
    private final LocalMemory memory;
    private boolean speaking = false;

    public TTSManager(TtsEngine engine, LocalMemory memory) {
        this.engine = engine;
        this.memory = memory;
    }

    public TtsEngine raw() {
        return engine;
    }

    /** True if the assistant is currently voicing something. */
    public boolean isSpeaking() {
        return speaking;
    }

    /** Stop any currently running TTS immediately. */
    public void stop() {
        speaking = false;
        engine.stop();
    }

    public boolean isMuted() {
        return memory.isMuted();
    }

    /** Apply the configured voice profile (male/female) live. */
    public void applyConfiguredVoice() {
        engine.setProfile(memory.isMaleVoice()
                ? VoiceProfile.JARVIS_MALE
                : VoiceProfile.ASSISTANT_FEMALE);
    }

    /**
     * Speak a response in the resolved language. Interrupts any running TTS.
     */
    public void speak(String text, String detectedInputLang) {
        if (text == null || text.isEmpty()) return;
        if (memory.isMuted()) return;
        speaking = true;
        // Submit on the engine's own thread gives a small yield; flush is
        // atomic enough here, but we explicitly stop first for priority.
        engine.stop();
        engine.speak(text, resolveLang(detectedInputLang));
    }

    /** Resolve output language from the bare utterance passed through. */
    private String resolveLang(String detectedInputLang) {
        LocalMemory.LanguageMode mode = memory.getLanguageMode();
        switch (mode) {
            case HINDI:
                return "hi";
            case ENGLISH:
                return "en";
            case AUTO:
            default:
                return "hi".equals(detectedInputLang) ? "hi" : "en";
        }
    }
}
