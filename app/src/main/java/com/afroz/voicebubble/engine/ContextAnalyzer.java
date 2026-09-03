package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Classifies current screen content into a high-level context, enabling JARVIS
 * to tailor how it explains things (Termux terminal errors vs. a settings
 * screen vs. a chat app). Also extracts time/progress when present.
 */
public class ContextAnalyzer {

    public enum ContextType {
        TERMINAL, ERROR, INSTALLATION, BUILD, CODE,
        FILE_MANAGER, SETTINGS, CHAT, BROWSER, UNKNOWN
    }

    /** An analysis result: context type + any detected progress/time. */
    public static class ScreenContext {
        public final ContextType type;
        public final String errorSummary;
        public final Integer progressPercent;   // nullable
        public final String remainingTime;      // e.g. "2 minutes", nullable

        public ScreenContext(ContextType type, String errorSummary,
                             Integer progressPercent, String remainingTime) {
            this.type = type;
            this.errorSummary = errorSummary;
            this.progressPercent = progressPercent;
            this.remainingTime = remainingTime;
        }
    }

    private final TerminalAnalyzer terminalAnalyzer = new TerminalAnalyzer();
    private final ErrorAnalyzer errorAnalyzer = new ErrorAnalyzer();

    public ScreenContext analyze(String text) {
        if (text == null) text = "";
        String lower = text.toLowerCase(Locale.US);

        // Error takes precedence — a blocking terminal error is most relevant.
        ErrorAnalyzer.Detection err = errorAnalyzer.detect(text);
        if (err != null) {
            return new ScreenContext(ContextType.ERROR, err.summaryEn,
                    progress(lower), remaining(lower));
        }

        if (lower.contains("npm ") || lower.contains("install ") || lower.contains("make ")
                || lower.contains("gradle ") || lower.contains("pip ")) {
            if (lower.contains("build") || lower.contains("compil")) {
                return new ScreenContext(ContextType.BUILD, null, progress(lower), remaining(lower));
            }
            return new ScreenContext(ContextType.INSTALLATION, null, progress(lower), remaining(lower));
        }

        if (terminalAnalyzer.isTerminalScan(lower)) {
            return new ScreenContext(ContextType.TERMINAL, null, progress(lower), remaining(lower));
        }

        if (lower.contains("{") && lower.contains("}") && lower.contains("def ")
                || lower.contains("function") || lower.contains("public static")
                || lower.contains("import ")) {
            return new ScreenContext(ContextType.CODE, null, null, null);
        }

        if (lower.contains("settings") || lower.contains("सेटिंग्स")) {
            return new ScreenContext(ContextType.SETTINGS, null, null, null);
        }
        if (lower.contains("chat") || lower.contains("whatsapp") || lower.contains("संदेश")) {
            return new ScreenContext(ContextType.CHAT, null, null, null);
        }
        if (lower.contains("browser") || lower.contains("http") || lower.contains("chrome")) {
            return new ScreenContext(ContextType.BROWSER, null, null, null);
        }
        if (lower.contains(".txt") || lower.contains(".py") || lower.contains(".java")
                || lower.contains("file")) {
            return new ScreenContext(ContextType.FILE_MANAGER, null, null, null);
        }

        return new ScreenContext(ContextType.UNKNOWN, null, null, null);
    }

    /** Extract a percentage (e.g. "72%", "45 percent"). Null when absent. */
    private Integer progress(String lower) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,3})\\s*(%|percent)").matcher(lower);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                return (v >= 0 && v <= 100) ? v : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Extract a "remaining time" hint. Null when absent. */
    private String remaining(String lower) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*(?:min|minute|sec|second|from now|बाकी|मिनट)")
                .matcher(lower);
        if (m.find()) return m.group(0);
        return null;
    }
}
