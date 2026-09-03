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
    private long lastStableTime = 0;
    private String pending = "";
    private final long stableDelayMs;

    public SubtitleDetector(long stableDelayMs) {
        this.stableDelayMs = stableDelayMs;
    }

    /**
     * Feed visible screen text; returns a NEW subtitle line (or null) when the
     * subtitle changed and stabilised. Never returns the same subtitle twice.
     */
    public String next(String screenText) {
        if (screenText == null) return null;
        String candidate = pickSubtitle(screenText);
        long now = System.currentTimeMillis();

        if (candidate == null) {
            return null;
        }

        String norm = normalize(candidate);
        if (norm.equals(lastSubtitle)) {
            // Same subtitle still visible: once stable, stop reporting.
            if (now - lastStableTime >= stableDelayMs && !pending.isEmpty()) {
                pending = "";
                return null;
            }
            return null;
        }

        // New subtitle: record, wait for it to stabilise briefly, then emit.
        lastSubtitle = norm;
        lastStableTime = now;
        pending = candidate;
        // Emit on the next stable frame (the caller will call repeatedly).
        if (now - lastStableTime >= stableDelayMs) {
            String out = pending;
            pending = "";
            return out;
        }
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
        pending = "";
        lastStableTime = 0;
    }
}
