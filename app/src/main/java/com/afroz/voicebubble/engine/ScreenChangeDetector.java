package com.afroz.voicebubble.engine;

import java.util.List;

/**
 * Intelligent screen-change detection.
 *
 * Only expensive OCR/context analysis should run when the screen content has
 * materially changed. This detector compares normalized recognized text,
 * waits for a stable frame (throttling), and reports whether the change is
 * significant enough to warrant processing. Reduces battery/CPU and prevents
 * re-reading the whole screen on every frame.
 */
public class ScreenChangeDetector {

    /** Debounce window: wait for the screen to settle before processing. */
    private final long stableDelayMs;
    private String lastSignature = "";
    private long lastChangeTime = 0;
    private String pendingText = "";

    public ScreenChangeDetector(long stableDelayMs) {
        this.stableDelayMs = stableDelayMs;
    }

    /**
     * Feed freshly recognised screen text. Returns the stabilised text to
     * process once content has settled AND changed significantly, or null.
     */
    public String next(String text) {
        if (text == null) text = "";
        String sig = signature(text);
        String current = lastSignature;
        long now = System.currentTimeMillis();

        if (sig.equals(current)) {
            // No change since last frame.
            if (now - lastChangeTime >= stableDelayMs && !pendingText.isEmpty()) {
                String stable = pendingText;
                pendingText = "";
                return stable;
            }
            return null;
        }

        // Significant change: record it and wait for it to stabilise.
        lastSignature = sig;
        lastChangeTime = now;
        pendingText = text;
        return null;
    }

    /** Reset state (e.g. on STOP / mode switch). */
    public void reset() {
        lastSignature = "";
        pendingText = "";
        lastChangeTime = 0;
    }

    private String signature(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Util: approximate Levenshtein variants are not needed; keep it simple. */
    @SuppressWarnings("unused")
    private static boolean significant(List<String> a, List<String> b) {
        return a != null && b != null && !a.equals(b);
    }
}
