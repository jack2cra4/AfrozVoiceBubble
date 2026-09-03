package com.afroz.voicebubble.chat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.speech.BilingualStt;
import com.afroz.voicebubble.speech.TtsEngine;

import java.util.Locale;

/**
 * Drives the ultra-responsive conversational loop.
 *
 * Uses a single {@link BilingualStt} recognizer that is automatically
 * restarted after every utterance so the assistant is always listening. It:
 *
 *  - Detects the JARVIS wake word ("हेलो जार्विस", "जार्विस", "Hello Jarvis")
 *    and immediately triggers a wake callback (halt TTS, pulse, deep-male
 *    reply) while keeping the listener live for the follow-up.
 *  - Routes every other utterance through {@link JarvisBrain} for clean
 *    bilingual answers and continues listening automatically.
 */
public class WakeWordListener {

    /** Ui-facing callbacks for the bubble overlay. */
    public interface Callback {
        /** Fired when the listener begins a listening session. */
        void onListening();
        /** Fired when a wake word is heard (bubble pulse, etc.). */
        void onWake();
        /** Fired for status text updates. */
        void onStatus(String status);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BilingualStt stt;
    private Callback callback;
    private boolean running = false;
    private boolean awake = false;
    private String lastLang = "en";

    public void start(Context context) {
        if (running) return;
        if (stt == null) stt = new BilingualStt(context);
        if (!stt.isAvailable()) {
            if (callback != null) callback.onStatus("Voice not available");
            return;
        }
        running = true;
        stt.setListener(createListener());
        listen();
    }

    public void stop() {
        running = false;
        awake = false;
        if (stt != null) stt.stopListening();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isAwake() {
        return awake;
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public void destroy() {
        stop();
        if (stt != null) {
            stt.destroy();
            stt = null;
        }
    }

    private BilingualStt.Listener createListener() {
        return new BilingualStt.Listener() {
            @Override
            public void onResult(String text, String lang) {
                if (!running) return;
                lastLang = "hi".equals(lang) ? "hi" : "en";
                handle(text, lastLang);
                // Auto-relisten for the next instruction.
                mainHandler.postDelayed(() -> {
                    if (running) listen();
                }, 250);
            }

            @Override
            public void onPartial(String text) {
                // Live-wake: react as soon as "jarvis" appears in partial text.
                if (running && !awake && containsWake(text)) {
                    fireWake(text == null ? "en" : TtsEngine.detectLang(text));
                }
            }

            @Override
            public void onError(String message) {
                if (!running) return;
                if (callback != null) callback.onStatus("Listening...");
                // Recognition ended without a result; retry shortly.
                mainHandler.postDelayed(() -> {
                    if (running) listen();
                }, 400);
            }
        };
    }

    private void listen() {
        if (!running || stt == null) return;
        try {
            stt.setSessionLang(BilingualStt.langToLocale(lastLang));
        } catch (Exception ignored) {}
        if (callback != null) callback.onListening();
        try {
            stt.startListening();
        } catch (Exception ignored) {}
    }

    private void handle(String text, String lang) {
        if (text == null) return;
        String lower = text.toLowerCase(Locale.US).trim();

        // Wake word / greeting -> immediate response, stay awake for follow-up.
        if (isWake(lower)) {
            awake = true;
            fireWake(lang);
            return;
        }

        // Any other utterance while the assistant is invoked: answer it.
        JarvisReply reply = App.get().getBrain().respond(text, lang);
        App.get().getTts().speak(reply.text, reply.lang);
        if (callback != null) callback.onStatus("..." );
    }

    private boolean containsWake(String text) {
        if (text == null) return false;
        return isWake(text.toLowerCase(Locale.US).trim());
    }

    private boolean isWake(String lower) {
        String t = lower.replaceAll("[!.,?]", "");
        return t.contains("jarvis") || t.contains("जार्विस") || t.contains("जारविस");
    }

    /**
     * Wake reaction: halt TTS, pulse the bubble, speak the deep-male wake
     * reply in the detected language, and keep the loop listening.
     */
    private void fireWake(String lang) {
        if (callback != null) callback.onWake();
        TtsEngine tts = App.get().getTts();
        tts.stop();
        JarvisReply reply = App.get().getBrain().respondWake(lang);
        if (callback != null) callback.onStatus("जार्विस उपस्थित");
        tts.speak(reply.text, reply.lang);
    }
}
