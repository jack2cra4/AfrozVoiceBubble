package com.afroz.voicebubble;

import android.app.Application;
import android.speech.tts.TextToSpeech;

import com.afroz.voicebubble.speech.TtsEngine;

public class App extends Application {
    private static App instance;
    private TtsEngine ttsEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ttsEngine = new TtsEngine(this);
    }

    public static App get() {
        return instance;
    }

    public TtsEngine getTts() {
        return ttsEngine;
    }
}
