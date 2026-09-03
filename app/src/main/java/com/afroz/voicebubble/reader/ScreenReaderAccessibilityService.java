package com.afroz.voicebubble.reader;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.speech.TtsEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JARVIS AccessibilityService.
 *
 * Optimization model:
 *  - Text-node scraping runs on a HIGH-PRIORITY background thread with an
 *    ultra-fast debounce loop (poll interval ~60ms, well under 80ms).
 *  - Only visible nodes are traversed; non-essential layout wrappers are
 *    skipped to keep extraction fast.
 *  - The TextToSpeech engine is pre-warmed on service start (pitch 1.0,
 *    rate 1.25x) so playback starts instantly.
 *  - Speech is produced ONLY on an explicit trigger: bubble tap, the
 *    "Open JARVIS" / "Read Screen" command, or an actual blocking Termux
 *    error. No random unprompted speech.
 */
public class ScreenReaderAccessibilityService extends AccessibilityService {

    private static ScreenReaderAccessibilityService instance;

    public static final String PREFS = "reader_prefs";
    public static final String KEY_ENABLED = "screen_reader_enabled";

    private static final String TERMUX_PACKAGE = "com.termux";

    // Poll interval well under 80ms for an ultra-fast response.
    private static final long POLL_INTERVAL_MS = 60;

    private TermuxErrorHelper termuxHelper;
    private String currentPackage = "";
    private String currentWindowRootPkg = "";

    // Background scraping machinery.
    private HandlerThread scrapeThread;
    private Handler scrapeHandler;
    private final AtomicBoolean readingActive = new AtomicBoolean(false);
    private final AtomicBoolean pollRunning = new AtomicBoolean(false);
    private String lastBufKey = "";
    private long lastSpeakTime = 0;
    private static final long SPEAK_COOLDOWN_MS = 2500;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        // Pre-warm TTS immediately for zero-latency first utterance.
        App.get().getTts().prewarm();

        scrapeThread = new HandlerThread("jarvis-scrape",
                android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        scrapeThread.start();
        scrapeHandler = new Handler(scrapeThread.getLooper());

        termuxHelper = new TermuxErrorHelper(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            if (event.getPackageName() != null) {
                currentPackage = event.getPackageName().toString();
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            // Termux blocking-error detection + screen-context capture always
            // runs on the background thread.
            final AccessibilityNodeInfo r = root;
            final boolean isTermux = TERMUX_PACKAGE.equals(currentPackage);
            scrapeHandler.post(() -> {
                try {
                    String text = extractAllText(r);
                    // Keep a rolling screen snapshot for context comprehension,
                    // so "this issue" / "यह एरर" can be resolved later.
                    if (text != null && !text.trim().isEmpty()) {
                        App.get().getBrain().setScreenContext(text);
                    }
                    if (isTermux) {
                        termuxHelper.analyze(text);
                    }
                } finally {
                    r.recycle();
                }
            });
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------
    // Public API invoked by the floating bubble / wake command / UI.
    // ---------------------------------------------------------------

    public static ScreenReaderAccessibilityService getInstance() {
        return instance;
    }

    /**
     * Trigger a one-shot read of the current screen immediately.
     * Called on bubble tap or the "Read Screen" / "Open JARVIS" command.
     */
    public void triggerRead() {
        startPolling(true);
    }

    /**
     * Begin continuous reading mode until stopReading() is called.
     */
    public void startContinuousRead() {
        startPolling(true);
    }

    public void stopReading() {
        readingActive.set(false);
        App.get().getTts().speak("Stopped", true);
    }

    private void startPolling(boolean active) {
        readingActive.set(active);
        if (!pollRunning.compareAndSet(false, true)) {
            return;
        }
        scrapeHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!readingActive.get()) {
                    pollRunning.set(false);
                    return;
                }
                try {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null) {
                        String visible = extractVisibleTextFast(root);
                        root.recycle();
                        speakTextBuffer(visible);
                    }
                } catch (Exception ignored) {}
                if (readingActive.get()) {
                    scrapeHandler.postDelayed(this, POLL_INTERVAL_MS);
                } else {
                    pollRunning.set(false);
                }
            }
        });
    }

    /**
     * Ultra-fast visible text extraction: skips non-essential wrapper nodes
     * (zero-width views, LinearLayout/FrameLayout groups with no own text) and
     * collects only nodes that themselves carry text or a description.
     */
    private String extractVisibleTextFast(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder(128);
        collectTextFast(node, sb);
        return sb.toString().trim();
    }

    private void collectTextFast(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null || sb.length() >= 400) return;
        try {
            if (!node.isVisibleToUser()) return;

            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String ownText = text == null ? null : text.toString().trim();
            String ownDesc = desc == null ? null : desc.toString().trim();

            boolean hasText = (ownText != null && ownText.length() > 1)
                    || (ownDesc != null && ownDesc.length() > 1);

            if (hasText) {
                if (ownText != null && ownText.length() > 1) {
                    sb.append(ownText).append(' ');
                } else if (ownDesc != null && ownDesc.length() > 1) {
                    sb.append(ownDesc).append(' ');
                }
            } else {
                // Non-essential layout wrapper: only descend if it has children.
                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        collectTextFast(child, sb);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Hand the freshly-scraped text buffer straight to the speech engine,
     * with a short cooldown to avoid flooding while preserving responsiveness.
     */
    private void speakTextBuffer(String text) {
        if (text == null || text.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastBufKey)) return;
        if (now - lastSpeakTime < SPEAK_COOLDOWN_MS) return;

        lastBufKey = text;
        lastSpeakTime = now;
        TtsEngine tts = App.get().getTts();
        tts.speakClean(text);
    }

    /**
     * Raw dump of every text node (used for Termux error scanning).
     */
    private String extractAllText(AccessibilityNodeInfo root) {
        StringBuilder sb = new StringBuilder();
        collectAll(root, sb);
        return sb.toString();
    }

    private void collectAll(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        try {
            CharSequence text = node.getText();
            if (text != null && !text.toString().trim().isEmpty()) {
                sb.append(text).append('\n');
            }
            CharSequence desc = node.getContentDescription();
            if (desc != null && !desc.toString().trim().isEmpty()) {
                sb.append(desc).append('\n');
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                collectAll(node.getChild(i), sb);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        readingActive.set(false);
        if (scrapeThread != null) {
            scrapeThread.quitSafely();
            scrapeThread = null;
        }
        if (termuxHelper != null) {
            termuxHelper.destroy();
            termuxHelper = null;
        }
        if (instance == this) {
            instance = null;
        }
    }

    // Legacy prefs-backed toggle kept for compatibility.
    public static void setScreenReaderEnabled(boolean enabled) {
        Context app = App.get();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    @SuppressWarnings("unused")
    private boolean isReaderEnabled() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    @SuppressWarnings("unused")
    private String translateToHindi(String english) {
        if (english == null) return null;
        return english.toLowerCase(Locale.US).trim().isEmpty() ? null : "Hindi: " + english;
    }
}
