package com.afroz.voicebubble;

import android.app.Application;

import com.afroz.voicebubble.chat.JarvisBrain;
import com.afroz.voicebubble.speech.TtsEngine;

public class App extends Application {
    private static App instance;
    private TtsEngine ttsEngine;
    private JarvisBrain jarvisBrain;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ttsEngine = new TtsEngine(this);
        jarvisBrain = new JarvisBrain();
    }

    public static App get() {
        return instance;
    }

    public TtsEngine getTts() {
        return ttsEngine;
    }

    public JarvisBrain getBrain() {
        return jarvisBrain;
    }
}
