package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Lightweight heuristic that recognises whether the current screen is a
 * terminal/conversational scan surface (Termux-like output) so JARVIS can
 * summarise instead of reading raw logs.
 */
public class TerminalAnalyzer {

    /**
     * True when the text looks like terminal/CLI output that should be
     * summarised rather than spoken verbatim.
     */
    public boolean isTerminalScan(String lower) {
        if (lower == null) return false;
        return lower.contains("$ ") || lower.contains("~")
                || lower.contains("termux") || lower.contains("bash")
                || lower.contains("/home") || lower.contains("root@")
                || lower.contains(">>>") || lower.matches("(?s).*\\n.*\\s#>.*");
    }

    /** Summarise the most important of a set of terminal lines. */
    public String summarize(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.matches("[\\[\\]{}():;=*#\\-]+")) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(t);
            count++;
            if (count >= 3) break;
        }
        String s = sb.toString();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
