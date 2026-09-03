package com.afroz.voicebubble;

import android.app.Application;

import com.afroz.voicebubble.agent.AgentManager;
import com.afroz.voicebubble.ai.AIProviderManager;
import com.afroz.voicebubble.chat.JarvisBrain;
import com.afroz.voicebubble.engine.ConversationManager;
import com.afroz.voicebubble.engine.JarvisStateManager;
import com.afroz.voicebubble.engine.LiveModeController;
import com.afroz.voicebubble.engine.LocalMemory;
import com.afroz.voicebubble.engine.MemoryManager;
import com.afroz.voicebubble.engine.SettingsManager;
import com.afroz.voicebubble.engine.SubtitleDetector;
import com.afroz.voicebubble.engine.TTSManager;
import com.afroz.voicebubble.engine.TaskManager;
import com.afroz.voicebubble.engine.TranslationManager;
import com.afroz.voicebubble.engine.VoiceManager;
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

    private SettingsManager settingsManager;
    private VoiceManager voiceManager;
    private TaskManager taskManager;
    private MemoryManager memoryManager;
    private AgentManager agentManager;
    private AIProviderManager providerManager;
    private TranslationManager translationManager;
    private SubtitleDetector subtitleDetector;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ttsEngine = new TtsEngine(this);
        jarvisBrain = new JarvisBrain();

        memory = new LocalMemory(this);
        settingsManager = new SettingsManager(this);
        ttsManager = new TTSManager(ttsEngine, memory);
        voiceManager = new VoiceManager(ttsManager, settingsManager);
        taskManager = new TaskManager(this);
        memoryManager = new MemoryManager(this);
        agentManager = new AgentManager(this);
        providerManager = new AIProviderManager(this, jarvisBrain);
        translationManager = new TranslationManager(settingsManager);
        subtitleDetector = new SubtitleDetector(600);

        stateManager = new JarvisStateManager();
        conversation = new ConversationManager(this, memory, settingsManager, ttsManager,
                stateManager, agentManager, providerManager, taskManager, translationManager,
                subtitleDetector, memoryManager, jarvisBrain);
        liveMode = new LiveModeController(this, memory, conversation, settingsManager);

        voiceManager.apply();
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

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public VoiceManager getVoiceManager() {
        return voiceManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public AgentManager getAgentManager() {
        return agentManager;
    }

    public AIProviderManager getProviderManager() {
        return providerManager;
    }

    public TranslationManager getTranslationManager() {
        return translationManager;
    }

    public SubtitleDetector getSubtitleDetector() {
        return subtitleDetector;
    }

    @Override
    public void onTerminate() {
        if (liveMode != null) liveMode.shutdown();
        super.onTerminate();
    }
}
