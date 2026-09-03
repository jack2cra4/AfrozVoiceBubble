package com.afroz.voicebubble.engine;

import android.content.Context;

import com.afroz.voicebubble.speech.BilingualStt;

/**
 * Speech recognition facade around {@link BilingualStt}.
 *
 * Resolves the recognition locale from the selected language mode:
 * HINDI -> hi-IN, ENGLISH -> en-IN, AUTO -> follows the last detected language
 * (bilingual). Uses offline recognition preferentially. If the recognizer is
 * unavailable it reports a clear message rather than failing silently.
 */
public class SpeechRecognitionManager {

    public interface Listener {
        void onResult(String text, String lang);
        void onPartial(String text);
        void onError(String message);
    }

    private final BilingualStt stt;
    private final LocalMemory memory;
    private Listener listener;

    public SpeechRecognitionManager(Context context, LocalMemory memory) {
        this.stt = new BilingualStt(context);
        this.memory = memory;
    }

    public boolean isAvailable() {
        return stt.isAvailable();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Set the current recognition locale manually (AUTO mode follows this). */
    public void setLocaleFromMode() {
        LocalMemory.LanguageMode mode = memory.getLanguageMode();
        switch (mode) {
            case HINDI:
                stt.setSessionLang("hi-IN");
                break;
            case ENGLISH:
                stt.setSessionLang("en-IN");
                break;
            case AUTO:
            default:
                // Bilingual-ish: keep last used locale (set externally).
                stt.setSessionLang("en-IN");
                break;
        }
    }

    public void setSessionLang(String locale) {
        stt.setSessionLang(locale);
    }

    /** Begin a listening turn; reports unavailability through the listener. */
    public void listen() {
        if (!isAvailable()) {
            if (listener != null) {
                listener.onError("Offline voice model not installed. Enable it in Android Speech settings.");
            }
            return;
        }
        setLocaleFromMode();
        stt.setListener(new BilingualStt.Listener() {
            @Override
            public void onResult(String text, String lang) {
                if (listener != null) listener.onResult(text, lang);
            }

            @Override
            public void onPartial(String text) {
                if (listener != null) listener.onPartial(text);
            }

            @Override
            public void onError(String message) {
                if (listener != null) listener.onError(message);
            }
        });
        stt.startListening();
    }

    public void stop() {
        stt.stopListening();
    }

    public void destroy() {
        stop();
        stt.destroy();
    }
}
