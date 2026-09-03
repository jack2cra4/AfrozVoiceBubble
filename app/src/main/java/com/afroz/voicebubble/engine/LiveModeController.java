package com.afroz.voicebubble.engine;

import android.content.Context;
import android.media.Image;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates live screen monitoring.
 *
 * On START it begins listening for screen changes and proactively nudges the
 * user when something important appears (an error, a finished build) — but
 * never reads raw logs or babbles. On STOP it halts all monitoring, OCR and
 * proactive speech, and no background work continues afterwards.
 *
 * Screen text comes from two local sources:
 *  1. The accessibility text scraper (always available, offline).
 *  2. Optional ML Kit OCR over captured media-projection frames.
 */
public class LiveModeController {

    private final LocalMemory memory;
    private final ConversationManager conversation;
    private final SettingsManager settings;
    private final ScreenChangeDetector changeDetector;
    private final OCRManager ocr;
    private final ScreenCaptureManager capture;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private volatile boolean live = false;
    private long lastProactiveTime = 0;
    private static final long PROACTIVE_COOLDOWN_MS = 20_000;

    public interface Ui {
        void speak(String text, String lang);
        void onState(JarvisStateManager.State s);
    }

    private Ui ui;

    public LiveModeController(Context context, LocalMemory memory,
                              ConversationManager conversation, SettingsManager settings) {
        this.memory = memory;
        this.conversation = conversation;
        this.settings = settings;
        this.changeDetector = new ScreenChangeDetector(350);
        this.ocr = new OCRManager();
        this.capture = new ScreenCaptureManager(context);
    }

    public void setUi(Ui ui) {
        this.ui = ui;
    }

    public boolean isLive() {
        return live;
    }

    /** Called when the user presses START. */
    public void start() {
        if (live) return;
        live = true;
        changeDetector.reset();
        conversation.startReaction();
        conversation.getState().startLive();
        notifyState(conversation.getState().getState());
        if (ui != null) {
            ui.speak(conversation.startReaction(), conversation.resolveLang());
        }
    }

    /** Called when the user presses STOP — halts everything. */
    public void stop() {
        if (!live) return;
        live = false;
        changeDetector.reset();
        String r = conversation.stopReaction();
        conversation.resetContext();
        if (ui != null) ui.speak(r, conversation.resolveLang());
        notifyState(conversation.getState().getState());
    }

    /**
     * Feed accessibility-scraped screen text (throttled + change-detected).
     * Proactively speaks only when something worth telling appears.
     */
    public void onAccessibilityScreenText(String text) {
        if (!live) return;
        conversation.ingestScreenText(text);
        String stable = changeDetector.next(text);
        if (stable != null) {
            maybeProactive();
        }
    }

    /** Optional OCR entry: recognise a captured frame and feed the result. */
    public void onCapturedFrame(Image frame) {
        if (!live || frame == null) return;
        worker.execute(() -> {
            AtomicReference<String> sink = new AtomicReference<>("");
            ocr.recognize(frame, 0, sink, () -> {
                String t = sink.get();
                if (t != null && !t.trim().isEmpty()) {
                    onAccessibilityScreenText(t);
                }
            });
        });
    }

    private void maybeProactive() {
        if (!settings.isProactive()) return;
        long now = System.currentTimeMillis();
        long cooldown = settings.isPerformanceMode() ? PROACTIVE_COOLDOWN_MS * 3
                                                     : PROACTIVE_COOLDOWN_MS;
        if (now - lastProactiveTime < cooldown) return;
        String p = conversation.proactiveUpdate();
        if (p != null) {
            lastProactiveTime = now;
            notifyState(conversation.getState().getState());
            if (ui != null) ui.speak(p, conversation.resolveLang());
        }
    }

    private void notifyState(JarvisStateManager.State s) {
        if (ui != null) ui.onState(s);
    }

    public ScreenCaptureManager getCapture() {
        return capture;
    }

    public boolean isOcrAvailable() {
        return ocr.isAvailable();
    }

    public void shutdown() {
        live = false;
        worker.shutdownNow();
        ocr.close();
        capture.stop();
    }
}
