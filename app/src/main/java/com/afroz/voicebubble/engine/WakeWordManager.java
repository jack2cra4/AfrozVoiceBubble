package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Detects the JARVIS wake word across Hindi and English variants and reacts
 * immediately: halt any running TTS, switch into active conversation mode, and
 * respond in the deep male voice while keeping the listener active.
 */
public class WakeWordManager {

    /** True if the utterance contains a JARVIS wake trigger. */
    public boolean isWake(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.US).trim().replaceAll("[!.,?]", "");
        // Avoid matching the assistant's own repeated "jarvis" chatter.
        return t.contains("jarvis") || t.contains("जार्विस") || t.contains("जारविस");
    }

    /** Also treat an explicit "hello jarvis" style greeting as a wake. */
    public boolean isWakeWithHello(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.US).trim();
        return t.contains("hello jarvis") || t.contains("हेलो जार्विस")
                || t.contains("हेलो जारविस");
    }
}
