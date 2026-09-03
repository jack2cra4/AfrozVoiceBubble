package com.afroz.voicebubble.speech;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * On-device speech recognition (STT) that prefers offline recognition when the
 * device has offline speech data installed. No network is used.
 */
public class SttEngine {

    public interface Listener {
        void onResult(String text);
        void onError(String message);
    }

    private SpeechRecognizer recognizer;
    private boolean available;
    private Listener listener;

    public SttEngine(Context context) {
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
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
                    if (listener != null) listener.onError("Recognition error: " + error);
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        if (listener != null) listener.onResult(matches.get(0));
                    } else if (listener != null) {
                        listener.onError("Nothing heard.");
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    public void startListening(String language) {
        if (recognizer == null) {
            if (listener != null) listener.onError("Speech recognizer not available.");
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra("android.speech.extra.PREFER_OFFLINE", true);
            recognizer.startListening(intent);
        } catch (Exception e) {
            if (listener != null) listener.onError("Speech recognition could not start.");
        }
    }

    public void stopListening() {
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (Exception ignored) {}
        }
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
