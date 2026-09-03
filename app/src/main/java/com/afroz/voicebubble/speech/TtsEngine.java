package com.afroz.voicebubble.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Offline Hindi (hi-IN) text-to-speech engine that falls back to default
 * device voices when Hindi is not installed, without any network access.
 */
public class TtsEngine {

    private final Context context;
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean usingHindi = false;

    public TtsEngine(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("hi", "IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    usingHindi = false;
                    tts.setLanguage(Locale.getDefault());
                } else {
                    usingHindi = true;
                }
                ready = true;
            }
        });
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isUsingHindi() {
        return usingHindi;
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
