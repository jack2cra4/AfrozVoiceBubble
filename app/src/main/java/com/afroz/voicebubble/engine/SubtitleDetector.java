package com.afroz.voicebubble.engine;

import java.util.regex.Pattern;

/**
 * Deduplicating subtitle detector.
 *
 * Monitors screen text for likely subtitle lines (short, self-contained,
 * changing over time — typical of video captions). It only emits a subtitle
 * when the visible subtitle text actually CHANGES; the same subtitle remaining
 * on screen is never re-emitted, preventing continuous repetition.
 */
public class SubtitleDetector {

    /** Minimum subtitle length to consider (avoids noise). */
    private static final int MIN_LEN = 6;
    /** Maximum short-line length typical of a single subtitle. */
    private static final int MAX_LEN = 160;

    private String lastSubtitle = "";
    private String pendingNorm = "";
    private long pendingSince = 0;
    private final long stableDelayMs;

    public SubtitleDetector(long stableDelayMs) {
        this.stableDelayMs = stableDelayMs;
    }

    /**
     * Feed visible screen text; returns a NEW subtitle line (or null) when a
     * subtitle changed and stayed stable for {@code stableDelayMs}. A given
     * subtitle is emitted exactly once and never repeated while unchanged.
     */
    public String next(String screenText) {
        if (screenText == null) return null;
        String candidate = pickSubtitle(screenText);
        if (candidate == null) {
            return null;
        }
        String norm = normalize(candidate);

        // Already spoken this exact subtitle -> never repeat it.
        if (norm.equals(lastSubtitle)) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (norm.equals(pendingNorm)) {
            // Same new subtitle on screen: emit once it has stabilised.
            if (now - pendingSince >= stableDelayMs) {
                lastSubtitle = norm;
                pendingNorm = "";
                pendingSince = 0;
                return candidate;
            }
            return null;
        }

        // A brand new subtitle: start waiting for it to stabilise.
        pendingNorm = norm;
        pendingSince = now;
        return null;
    }

    /** Best-effort pick of a single short sub-caption line from screen text. */
    private String pickSubtitle(String screenText) {
        String[] lines = screenText.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.length() < MIN_LEN || t.length() > MAX_LEN) continue;
            if (looksLikeSubtitle(t)) return t;
        }
        return null;
    }

    private boolean looksLikeSubtitle(String t) {
        // Subtitles are short natural sentences; exclude obvious UI/terminal
        // noise (paths, long tokens, symbols).
        if (t.contains("/") || t.contains("\\")) return false;
        if (t.matches(".*\\d{2,}.*")) return false;      // long numbers = likely log
        if (t.replaceAll("[A-Za-z ]", "").length() > 4) return false; // weird chars
        return true;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    public void reset() {
        lastSubtitle = "";
        pendingNorm = "";
        pendingSince = 0;
    }
}
