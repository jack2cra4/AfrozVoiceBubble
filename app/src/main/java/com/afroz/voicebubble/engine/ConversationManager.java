package com.afroz.voicebubble.engine;

import android.content.Context;

import com.afroz.voicebubble.agent.Agent;
import com.afroz.voicebubble.agent.AgentManager;
import com.afroz.voicebubble.ai.AIProviderManager;
import com.afroz.voicebubble.chat.JarvisBrain;
import com.afroz.voicebubble.engine.ContextAnalyzer.ScreenContext;
import com.afroz.voicebubble.engine.ErrorAnalyzer.Detection;

import java.util.List;
import java.util.Locale;

/**
 * Central conversation orchestrator.
 *
 * Holds the live session context (last recognised screen text, analysed
 * context, last error, subtitle state) and drives the full flow from a spoken
 * utterance to a spoken, localised response. It integrates:
 *
 *   - the offline intent parser + response generator (always available);
 *   - optional cloud/AI-routed answers via {@link AIProviderManager} with an
 *     automatic local fallback;
 *   - an agent router (multi-agent delegation, summarised to the user);
 *   - task monitoring / reminders;
 *   - subtitle detection + translation.
 *
 * Pipeline: utterance -&gt; IntentParser -&gt; (Agent/AI route) -&gt; Response -&gt; TTS.
 */
public class ConversationManager {

    private final Context context;
    private final LocalMemory memory;
    private final SettingsManager settings;
    private final TTSManager tts;
    private final JarvisStateManager stateManager;
    private final AgentManager agents;
    private final AIProviderManager providers;
    private final TaskManager tasks;
    private final TranslationManager translation;
    private final SubtitleDetector subtitles;
    private final MemoryManager sessionMemory;
    private final JarvisBrain brain;

    private final IntentParser intentParser;
    private final ContextAnalyzer contextAnalyzer;
    private final ErrorAnalyzer errorAnalyzer;
    private final ResponseGenerator responseGenerator;
    private final WakeWordManager wakeWordManager;

    // Live session context.
    private volatile String lastScreenText = "";
    private volatile ScreenContext lastScreenContext = null;
    private volatile Detection lastError = null;
    private volatile String lastLanguage = "hi";

    // Task being actively monitored (id or null).
    private volatile String monitoredTaskId = null;

    public interface WakeHook {
        void onWakeDetected();
    }
    private WakeHook wakeHook;

    public ConversationManager(Context context, LocalMemory memory,
                               SettingsManager settings, TTSManager tts,
                               JarvisStateManager stateManager,
                               AgentManager agents, AIProviderManager providers,
                               TaskManager tasks, TranslationManager translation,
                               SubtitleDetector subtitles, MemoryManager sessionMemory,
                               JarvisBrain brain) {
        this.context = context.getApplicationContext();
        this.memory = memory;
        this.settings = settings;
        this.tts = tts;
        this.stateManager = stateManager;
        this.agents = agents;
        this.providers = providers;
        this.tasks = tasks;
        this.translation = translation;
        this.subtitles = subtitles;
        this.sessionMemory = sessionMemory;
        this.brain = brain;
        this.intentParser = new IntentParser();
        this.contextAnalyzer = new ContextAnalyzer();
        this.errorAnalyzer = new ErrorAnalyzer();
        this.responseGenerator = new ResponseGenerator(memory);
        this.wakeWordManager = new WakeWordManager();
    }

    public void setWakeHook(WakeHook hook) {
        this.wakeHook = hook;
    }

    public JarvisStateManager getState() { return stateManager; }
    public IntentParser getIntentParser() { return intentParser; }
    public ResponseGenerator getResponseGenerator() { return responseGenerator; }
    public ErrorAnalyzer getErrorAnalyzer() { return errorAnalyzer; }
    public ContextAnalyzer getContextAnalyzer() { return contextAnalyzer; }
    public TranslationManager getTranslationManager() { return translation; }
    public SubtitleDetector getSubtitleDetector() { return subtitles; }

    // ---------------------------------------------------------------
    // Screen context ingestion (from accessibility scraping / OCR)
    // ---------------------------------------------------------------

    public void ingestScreenText(String text) {
        if (text == null) text = "";
        lastScreenText = text;
        lastScreenContext = contextAnalyzer.analyze(text);
        lastError = errorAnalyzer.detect(text);

        // Subtitle detection (deduplicated).
        String sub = subtitles.next(text);
        if (sub != null) {
            handleNewSubtitle(sub);
        }

        // Task monitoring: update a monitored task from observable screen state.
        updateMonitoredTask(text);
    }

    public String getLastScreenText() { return lastScreenText; }
    public ScreenContext getLastScreenContext() { return lastScreenContext; }
    public Detection getLastError() { return lastError; }
    public String getLastLanguage() { return lastLanguage; }

    // ---------------------------------------------------------------
    // Subtitle handling
    // ---------------------------------------------------------------

    private void handleNewSubtitle(String subtitle) {
        String mode = settings.getSubtitleMode();
        if ("off".equals(mode)) return;
        boolean translate = settings.isTranslateSubtitles();
        String target = translate ? resolveLang() : "en";
        String explanation = translate
                ? translation.translate(subtitle, target)
                : subtitle;
        boolean shouldSpeak = settings.isSpeakTranslation();
        if (mode.equals("important") || mode.equals("full") || shouldSpeak) {
            speakFramed(explanation);
        }
    }

    // ---------------------------------------------------------------
    // Task monitoring
    // ---------------------------------------------------------------

    /** User asked to monitor a task — remember and acknowledge. */
    public String commandMonitorTask(String taskName) {
        TaskManager.Task t = tasks.create(taskName, TaskManager.Status.RUNNING, null);
        monitoredTaskId = t.id;
        boolean hi = isHi();
        return hi ? ("जी सर, \"" + t.name + "\" को monitor कर रहा हूँ।")
                  : ("Yes sir, I am monitoring \"" + t.name + "\".");
    }

    public String commandListTasks() {
        boolean hi = isHi();
        List<TaskManager.Task> all = tasks.all();
        if (all.isEmpty()) {
            return hi ? "सर, अभी कोई task नहीं है।" : "Sir, there are no tasks right now.";
        }
        StringBuilder sb = new StringBuilder(hi ? "सर, आपके tasks: " : "Sir, your tasks: ");
        for (int i = 0; i < all.size() && i < 3; i++) {
            TaskManager.Task t = all.get(i);
            if (i > 0) sb.append("; ");
            sb.append(t.status == TaskManager.Status.RUNNING
                    ? (hi ? "चल रहा है - " : "running - ") : t.status.name())
              .append(t.name);
        }
        return sb.toString();
    }

    private void updateMonitoredTask(String screenText) {
        if (monitoredTaskId == null) return;
        TaskManager.Task t = tasks.find(monitoredTaskId);
        if (t == null) { monitoredTaskId = null; return; }
        if (t.status != TaskManager.Status.RUNNING) return;
        String lower = (screenText == null ? "" : screenText.toLowerCase(Locale.US));
        if (containsAny(lower, "error", "failed", "exception", "erresolve", "npm err")) {
            tasks.setStatus(monitoredTaskId, TaskManager.Status.FAILED);
            boolean hi = isHi();
            speakFramed(hi ? "सर, आपका task fail हुआ है, स्क्रीन पर error दिख रहा है।"
                           : "Sir, your task failed; there is an error on screen.");
            monitoredTaskId = null;
        } else if (containsAny(lower, "success", "build successful", "done", "built successfully")) {
            tasks.setStatus(monitoredTaskId, TaskManager.Status.COMPLETED);
            boolean hi = isHi();
            speakFramed(hi ? "सर, आपका task पूरा हो गया है।"
                           : "Sir, your task has completed.");
            monitoredTaskId = null;
        }
    }

    // ---------------------------------------------------------------
    // Utterance handling (full pipeline with agents/AI/tasks)
    // ---------------------------------------------------------------

    public String respondToSpeech(String text, String detectedLang) {
        if (text == null) return "";
        lastLanguage = "hi".equals(detectedLang) ? "hi" : "en";

        if (wakeWordManager.isWake(text)) {
            if (wakeHook != null) wakeHook.onWakeDetected();
            stateManager.onWake();
            return responseGenerator.respond(IntentParser.Intent.WAKE,
                    resolveLang(), lastScreenContext, lastError);
        }

        stateManager.onProcessing();
        IntentParser.ParsedIntent parsed = intentParser.parse(text);

        String lower = text.toLowerCase(Locale.US).trim();

        // Task / memory command routes first (local, always available).
        if (containsAny(lower, "monitor", "याद रखना", "track", "monitor karo")) {
            String r = commandMonitorTask(deriveTaskName(text));
            stateManager.onSpeaking();
            return r;
        }
        if (containsAny(lower, "tasks dikhao", "कौन से task", "list tasks", "my tasks")) {
            String r = commandListTasks();
            stateManager.onSpeaking();
            return r;
        }

        // Translation commands.
        if (containsAny(lower, "translate", "हिंदी में समझाओ", "translate this", "इसका मतलब",
                "samjhao hindi", "समझाओ")) {
            String r = translateScreen();
            stateManager.onSpeaking();
            return r.isEmpty()
                    ? (isHi() ? "सर, स्क्रीन पर देखने लायक कुछ नहीं मिला।" : "Sir, nothing to translate on screen.")
                    : r;
        }
        if (containsAny(lower, "subtitle", "subtitle kya", "यह subtitle")) {
            String r = translation.translate(lastScreenText, resolveLang());
            stateManager.onSpeaking();
            return r.isEmpty() ? (isHi() ? "सर, subtitle नहीं दिख रहा।" : "Sir, no subtitle on screen.")
                               : r;
        }

        // Complex questions: offload to the AI router when enabled, with local fallback.
        if (shouldUseAI(parsed.intent)) {
            String ai = routeAgentAnswer(text);
            if (ai != null && !ai.trim().isEmpty()) {
                stateManager.onSpeaking();
                return ai;
            }
        }

        // Offline local interpretation otherwise.
        String response = responseGenerator.respond(parsed.intent, resolveLang(),
                lastScreenContext, lastError);
        stateManager.onSpeaking();
        return response;
    }

    private String routeAgentAnswer(String text) {
        Agent termux = bestAgentFor(text);
        String system = (termux != null && termux.enabled && termux.instructions != null)
                ? termux.instructions : null;
        if (settings.isLocalOnly()) {
            return localAnswer(text);
        }
        String r = providers.routeAny(text, system);
        return (r == null || r.trim().isEmpty()) ? localAnswer(text) : r;
    }

    private String localAnswer(String text) {
        String lang = isHi() ? "hi" : "en";
        com.afroz.voicebubble.chat.JarvisReply reply = brain.respond(text, lang);
        return reply == null ? null : reply.text;
    }

    private Agent bestAgentFor(String text) {
        String lower = text.toLowerCase(Locale.US);
        List<Agent> enabled = agents.enabled();
        Agent best = null;
        int score = -1;
        for (Agent a : enabled) {
            int s = 0;
            if (a.name != null) {
                String n = a.name.toLowerCase(Locale.US);
                if (lower.contains("error") && n.contains("DEBUGGER")) s += 2;
                if (lower.contains("terminal") && n.contains("TERMUX")) s += 3;
                if (lower.contains("code") && n.contains("CODER")) s += 2;
                if (lower.contains("android") && n.contains("ANDROID")) s += 2;
                if (lower.contains("screen") && n.contains("SCREEN")) s += 2;
                if (lower.contains("translate") && n.contains("TRANSLATOR")) s += 3;
            }
            if (s > score) { score = s; best = a; }
        }
        return best != null ? best : (enabled.isEmpty() ? null : enabled.get(0));
    }

    public String translateScreen() {
        String target = resolveLang();
        return translation.translate(lastScreenText, target);
    }

    // ---------------------------------------------------------------
    // Language / settings helpers
    // ---------------------------------------------------------------

    public String resolveLang() {
        String mode = settings.getLanguageMode();
        switch (mode) {
            case "hi": return "hi";
            case "en": return "en";
            case "auto": default: return lastLanguage;
        }
    }

    private boolean isHi() { return "hi".equals(resolveLang()); }

    private boolean shouldUseAI(IntentParser.Intent intent) {
        switch (intent) {
            case EXPLAIN_ERROR:
            case HOW_TO_FIX:
            case WHAT_IS_RUNNING:
            case NEXT_STEP:
                return true;
            default:
                return false;
        }
    }

    private String deriveTaskName(String text) {
        String name = text.replaceFirst("(?i)^(jarvis|hello)\\s*", "");
        name = name.replaceAll("(?i)(monitor|track|याद रखना|monitor karo)", " ").trim();
        if (name.length() > 40) name = name.substring(0, 40);
        return name.isEmpty() ? "Task" : name;
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    private void speakFramed(String s) {
        if (s == null || s.isEmpty()) return;
        tts.speak(s, resolveLang());
    }

    public String startReaction() {
        stateManager.onWake();
        return responseGenerator.respond(IntentParser.Intent.WAKE,
                resolveLang(), lastScreenContext, lastError);
    }

    public String stopReaction() {
        stateManager.stop();
        return responseGenerator.respond(IntentParser.Intent.STOP_ASSISTANT,
                resolveLang(), lastScreenContext, lastError);
    }

    /** Proactive update if something is worth telling (or null to stay quiet). */
    public String proactiveUpdate() {
        return responseGenerator.proactive(isHi(), lastScreenContext);
    }

    public void resetContext() {
        lastScreenText = "";
        lastScreenContext = null;
        lastError = null;
        subtitles.reset();
    }
}
