package com.afroz.voicebubble;

import android.app.Application;

import com.afroz.voicebubble.chat.JarvisBrain;
import com.afroz.voicebubble.engine.ConversationManager;
import com.afroz.voicebubble.engine.JarvisStateManager;
import com.afroz.voicebubble.engine.LiveModeController;
import com.afroz.voicebubble.engine.LocalMemory;
import com.afroz.voicebubble.engine.TTSManager;
import com.afroz.voicebubble.speech.TtsEngine;

public class App extends Application {
    private static App instance;
    private TtsEngine ttsEngine;
    private JarvisBrain jarvisBrain;

    private LocalMemory memory;
    private TTSManager ttsManager;
    private JarvisStateManager stateManager;
    private ConversationManager conversation;
    private LiveModeController liveMode;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ttsEngine = new TtsEngine(this);
        jarvisBrain = new JarvisBrain();

        memory = new LocalMemory(this);
        ttsManager = new TTSManager(ttsEngine, memory);
        stateManager = new JarvisStateManager();
        conversation = new ConversationManager(memory, stateManager);
        liveMode = new LiveModeController(this, memory, conversation);
        ttsManager.applyConfiguredVoice();
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

    public LocalMemory getMemory() {
        return memory;
    }

    public TTSManager getTtsManager() {
        return ttsManager;
    }

    public JarvisStateManager getStateManager() {
        return stateManager;
    }

    public ConversationManager getConversation() {
        return conversation;
    }

    public LiveModeController getLiveMode() {
        return liveMode;
    }

    @Override
    public void onTerminate() {
        if (liveMode != null) liveMode.shutdown();
        super.onTerminate();
    }
}
