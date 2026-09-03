package com.afroz.voicebubble.ai;

import com.afroz.voicebubble.chat.JarvisBrain;

/**
 * Offline local AI provider.
 *
 * Answers using the on-device engine (JarvisBrain). Always available and never
 * depends on a network. This is the default fallback when no cloud provider is
 * configured or when a cloud call fails — keeping JARVIS fully usable offline.
 */
public class LocalAIProvider implements AIProvider {

    private final JarvisBrain brain;

    public LocalAIProvider(JarvisBrain brain) {
        this.brain = brain;
    }

    @Override
    public String name() { return "LOCAL"; }

    @Override
    public String model() { return "jarvis-local"; }

    @Override
    public boolean isConfigured() { return true; }

    @Override
    public String complete(String prompt, String systemPrompt) {
        // Use the local brain; the user's query is interpreted offline.
        String lang = com.afroz.voicebubble.speech.TtsEngine.detectLang(prompt);
        com.afroz.voicebubble.chat.JarvisReply r = brain.respond(prompt, lang);
        return r == null ? null : r.text;
    }
}
