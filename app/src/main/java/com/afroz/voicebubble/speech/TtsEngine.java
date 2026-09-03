package com.afroz.voicebubble.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Offline bilingual (hi-IN / en-IN) text-to-speech engine that dynamically
 * switches its output voice based on the detected input language, without any
 * network access. Hi-IN falls back to the default device voice when Hindi is
 * not installed.
 */
public class TtsEngine {

    /** Current conversation language tag ("hi" or "en"), auto-switched. */
    private String activeLang = "en";

    private final Context context;
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean usingHindi = false;

    public TtsEngine(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Probe for a Hindi voice but don't lock the engine to it:
                // the active language is switched per utterance below.
                int result = tts.setLanguage(new Locale("hi", "IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    usingHindi = false;
                    tts.setLanguage(Locale.getDefault());
                } else {
                    usingHindi = true;
                }
                // Ultra-fast, natural playback: pitch 1.0, speech rate 1.25x.
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.25f);
                ready = true;
            }
        });
    }

    /**
     * Pre-warm the engine so the first utterance starts instantly.
     * Called as early as possible (service start).
     */
    public void prewarm() {
        if (ready && tts != null) {
            tts.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "afroz_prewarm");
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isUsingHindi() {
        return usingHindi;
    }

    /** Current active language tag ("hi" or "en"). */
    public String getActiveLang() {
        return activeLang;
    }

    /** Auto-detect language ("hi"/"en") from text script (Devanagari check). */
    public static String detectLang(String text) {
        if (text == null) return "en";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) return "hi";
        }
        return "en";
    }

    /**
     * Set the active conversation language and switch the underlying TTS voice
     * to match. Used for dynamic language switching between turns.
     *
     * @return true if the switch succeeded (or en, which always succeeds).
     */
    public boolean setLanguage(String lang) {
        if (!ready || tts == null) return false;
        try {
            if ("hi".equals(lang)) {
                if (!usingHindi) return false;
                tts.setLanguage(new Locale("hi", "IN"));
                activeLang = "hi";
            } else {
                tts.setLanguage(Locale.ENGLISH);
                activeLang = "en";
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Speak in the given language ("hi" or "en"), switching the voice
     * automatically for that utterance. Falls back to English TTS when the
     * requested language can't be voiced.
     */
    public void speak(String text, String lang) {
        String requested = (lang == null) ? activeLang : lang;
        if ("hi".equals(requested)) {
            if (usingHindi) {
                tts.setLanguage(new Locale("hi", "IN"));
                activeLang = "hi";
            } else {
                tts.setLanguage(Locale.ENGLISH);
                activeLang = "en";
            }
        } else {
            tts.setLanguage(Locale.ENGLISH);
            activeLang = "en";
        }
        speak(text, true);
    }

    /** Speak using the current active language (auto-detected from text). */
    public void speakAuto(String text) {
        speak(text, detectLang(text));
    }

    public void speak(String text) {
        speak(text, true);
    }

    public void speak(String text, boolean flush) {
        if (!ready || tts == null || text == null || text.isEmpty()) {
            return;
        }
        int queue = flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
        // minSdk is 21, so the utterance-id overload is always available.
        tts.speak(text, queue, null, "afroz_utterance");
    }

    /**
     * Speak scraped screen text immediately (flush) for the fastest possible
     * response, skipping any leading/trailing whitespace.
     */
    public void speakClean(String text) {
        if (text == null) return;
        String clean = text.trim();
        if (clean.isEmpty() || !ready || tts == null) return;
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "afroz_utterance");
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void setListener(UtteranceProgressListener listener) {
        if (tts != null) {
            tts.setOnUtteranceProgressListener(listener);
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
