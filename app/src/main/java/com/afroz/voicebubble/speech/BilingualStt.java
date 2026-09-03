package com.afroz.voicebubble.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Bilingual (Hindi + English) speech recognition wrapper.
 *
 * Supports dual-mode recognition: en-IN and hi-IN. The language can be set
 * explicitly for a session (e.g. following the last detected input language)
 * and the recognized text's language is reported by script (Devanagari = hi).
 *
 * The recognizer is recreated as needed so the recognition locale can change
 * between turns, enabling true dynamic language switching.
 */
public class BilingualStt {

    public interface Listener {
        /** @param text the recognized utterance */
        void onResult(String text, String detectedLang);
        void onPartial(String text);
        void onError(String message);
    }

    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean available;
    private Listener listener;
    private String sessionLang = "en-IN";

    public BilingualStt(Context context) {
        this.context = context.getApplicationContext();
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this.context);
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Current recognition locale, e.g. "en-IN" or "hi-IN". */
    public String getSessionLang() {
        return sessionLang;
    }

    /** Switch the recognition locale for the next/ongoing session. */
    public void setSessionLang(String locale) {
        this.sessionLang = locale;
    }

    public void setListener(Listener l) {
        this.listener = l;
        if (recognizer != null) {
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    emitError("Recognition error: " + error);
                }
                @Override public void onResults(Bundle results) {
                    try {
                        ArrayList<String> matches =
                                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0);
                            if (listener != null) {
                                listener.onResult(text, detect(text));
                            }
                        } else {
                            emitError("Nothing heard.");
                        }
                    } catch (Throwable ignored) {} // never crash the STT binder thread
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    try {
                        ArrayList<String> parts =
                                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (parts != null && !parts.isEmpty() && listener != null) {
                            listener.onPartial(parts.get(0));
                        }
                    } catch (Throwable ignored) {}
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    /** Start listening using the current session language. */
    public void startListening() {
        if (recognizer == null) {
            emitError("Speech recognizer not available.");
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sessionLang);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
            intent.putExtra("android.speech.extra.PREFER_OFFLINE", true);
            recognizer.startListening(intent);
        } catch (Exception e) {
            emitError("Speech recognition could not start.");
        }
    }

    /** Safely notify the listener of an error (binder thread safe). */
    private void emitError(String message) {
        if (listener == null) return;
        try {
            listener.onError(message);
        } catch (Throwable ignored) {
            // A listener failure must never crash the STT binder thread.
        }
    }

    public void stopListening() {
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (Exception ignored) {}
        }
    }

    // Script-based language detection for the recognized text.
    private static String detect(String text) {
        if (text == null) return "en";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) return "hi";
        }
        return "en";
    }

    /** Map a detected/tagged language ("hi"/"en") to a recognition locale. */
    public static String langToLocale(String lang) {
        return "hi".equals(lang) ? "hi-IN" : "en-IN";
    }

    public void destroy() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }
}
