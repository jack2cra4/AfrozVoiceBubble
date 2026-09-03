package com.afroz.voicebubble.engine;

import com.afroz.voicebubble.engine.ContextAnalyzer.ScreenContext;
import com.afroz.voicebubble.engine.ErrorAnalyzer.Detection;

import java.util.Locale;

/**
 * Central conversation orchestrator.
 *
 * Holds the current session context (last recognised screen text + analysed
 * context + last error detection) and drives the flow from a spoken utterance
 * to a spoken, localised response. It also reacts to the wake word and exposes
 * the state machine transitions used by the live-mode controller.
 *
 * The pipeline:
 *   utterance -> IntentParser -> ResponseGenerator -> TTSManager
 */
public class ConversationManager {

    private final LocalMemory memory;
    private final IntentParser intentParser;
    private final ContextAnalyzer contextAnalyzer;
    private final ErrorAnalyzer errorAnalyzer;
    private final ResponseGenerator responseGenerator;
    private final JarvisStateManager stateManager;
    private final WakeWordManager wakeWordManager;

    // Live session context.
    private volatile String lastScreenText = "";
    private volatile ScreenContext lastScreenContext = null;
    private volatile Detection lastError = null;
    private volatile String lastLanguage = "hi";

    // Wake reaction hook (set by UI/controller for pulse + stop).
    public interface WakeHook {
        void onWakeDetected();
    }
    private WakeHook wakeHook;

    public ConversationManager(LocalMemory memory, JarvisStateManager stateManager) {
        this.memory = memory;
        this.stateManager = stateManager;
        this.intentParser = new IntentParser();
        this.contextAnalyzer = new ContextAnalyzer();
        this.errorAnalyzer = new ErrorAnalyzer();
        this.responseGenerator = new ResponseGenerator(memory);
        this.wakeWordManager = new WakeWordManager();
    }

    public void setWakeHook(WakeHook hook) {
        this.wakeHook = hook;
    }

    public JarvisStateManager getState() {
        return stateManager;
    }

    public IntentParser getIntentParser() {
        return intentParser;
    }

    public ResponseGenerator getResponseGenerator() {
        return responseGenerator;
    }

    public ErrorAnalyzer getErrorAnalyzer() {
        return errorAnalyzer;
    }

    public ContextAnalyzer getContextAnalyzer() {
        return contextAnalyzer;
    }

    // ---------------------------------------------------------------
    // Screen context ingestion (from OCR / accessibility scraping)
    // ---------------------------------------------------------------

    /**
     * Feed a newly recognised screen text. Re-analyses lazily so proactive
     * updates and "this error" queries resolve against fresh context.
     */
    public void ingestScreenText(String text) {
        if (text == null) text = "";
        lastScreenText = text;
        lastScreenContext = contextAnalyzer.analyze(text);
        lastError = errorAnalyzer.detect(text);
    }

    public String getLastScreenText() {
        return lastScreenText;
    }

    public ScreenContext getLastScreenContext() {
        return lastScreenContext;
    }

    public Detection getLastError() {
        return lastError;
    }

    public String getLastLanguage() {
        return lastLanguage;
    }

    // ---------------------------------------------------------------
    // Utterance handling
    // ---------------------------------------------------------------

    /** Process a spoken utterance and return the response text (already
     * localised). @param detectedLang "hi"/"en" from the recognizer. */
    public String respondToSpeech(String text, String detectedLang) {
        if (text == null) return "";
        lastLanguage = "hi".equals(detectedLang) ? "hi" : "en";

        // Wake word interrupts everything immediately.
        if (wakeWordManager.isWake(text)) {
            if (wakeHook != null) wakeHook.onWakeDetected();
            stateManager.onWake();
            return responseGenerator.respond(IntentParser.Intent.WAKE,
                    resolveLang(), lastScreenContext, lastError);
        }

        stateManager.onProcessing();
        IntentParser.ParsedIntent parsed = intentParser.parse(text);
        String response = responseGenerator.respond(parsed.intent, resolveLang(),
                lastScreenContext, lastError);
        stateManager.onSpeaking();
        return response;
    }

    /** Resolve the output language from the configured mode. */
    public String resolveLang() {
        switch (memory.getLanguageMode()) {
            case HINDI: return "hi";
            case ENGLISH: return "en";
            case AUTO: default: return lastLanguage;
        }
    }

    /** Wake response used on START and on wake word. */
    public String startReaction() {
        stateManager.onWake();
        return responseGenerator.respond(IntentParser.Intent.WAKE,
                resolveLang(), lastScreenContext, lastError);
    }

    /** A clean confirmation spoken on STOP. */
    public String stopReaction() {
        stateManager.stop();
        return responseGenerator.respond(IntentParser.Intent.STOP_ASSISTANT,
                resolveLang(), lastScreenContext, lastError);
    }

    /** Proactive update if something is worth telling (or null to stay quiet). */
    public String proactiveUpdate() {
        return responseGenerator.proactive("hi".equals(resolveLang()), lastScreenContext);
    }

    /** Reset session context (on STOP / mode switch). */
    public void resetContext() {
        lastScreenText = "";
        lastScreenContext = null;
        lastError = null;
    }
}
