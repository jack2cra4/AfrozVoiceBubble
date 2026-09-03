package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Local, offline translation and explanation layer.
 *
 * Translates / explains visible screen text (English subtitles to Hindi and
 * Hindi screen text to English) using a lightweight phrase/intent map plus an
 * optional cloud provider (wired separately). The assistant never reads raw
 * text verbatim; it produces a natural, concise explanation.
 *
 * This is NOT a full statistical translator — it provides useful local
 * explanation plus a best-effort structural translation, and stays fully
 * usable offline.
 */
public class TranslationManager {

    private final SettingsManager settings;

    public TranslationManager(SettingsManager settings) {
        this.settings = settings;
    }

    /** Source language of a text: "hi" if it contains Devanagari, else "en". */
    public String detect(String text) {
        if (text == null) return "en";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) return "hi";
        }
        return "en";
    }

    /**
     * Produce a natural explanation of a visible English subtitle/sentence
     * into Hindi (or keep English). Falls back to a clean structural
     * rendering when no mapping is known.
     */
    public String explainToHindi(String english) {
        if (english == null || english.trim().isEmpty()) return "";
        String lower = english.toLowerCase(Locale.US).trim();

        // Known phrase mappings (common tutorial/terminal/setup lines).
        if (lower.contains("out of memory") || lower.contains("running out of memory")) {
            return "सर, इसका मतलब है कि आपके सिस्टम की memory कम पड़ रही है।";
        }
        if (lower.contains("install the dependencies") || lower.contains("install dependencies")) {
            return "सर, यहाँ बता रहा है कि पहले dependencies install करनी हैं।";
        }
        if (lower.contains("first, install") || lower.contains("install the")) {
            return "सर, यह बता रहा है कि पहले यह install करना है।";
        }
        if (lower.contains("run the command") || lower.contains("type the command")) {
            return "सर, यह कह रहा है कि यह command run करनी है।";
        }
        if (lower.contains("press enter")) {
            return "सर, अब Enter दबाना है।";
        }
        if (lower.contains("open the terminal") || lower.contains("open terminal")) {
            return "सर, यह terminal खोलने के लिए कह रहा है।";
        }
        if (lower.contains("wait for") ) {
            return "सर, यह इंतज़ार करने के लिए कह रहा है।";
        }
        if (lower.contains("download")) {
            return "सर, यहाँ download हो रहा है।";
        }
        if (lower.contains("error")) {
            return "सर, यह एक error मैसेज है। मैं इसका विवरण समझा सकता हूँ।";
        }
        if (lower.contains("success") || lower.contains("completed") || lower.contains("done")) {
            return "सर, यह कह रहा है कि कार्य पूरा हो गया।";
        }
        if (lower.contains("please") && lower.contains("check")) {
            return "सर, यह जाँचने के लिए कह रहा है।";
        }

        // Fallback: clean structural rendering, capped and not raw-verbose.
        return "सर, स्क्रीन पर यह दिख रहा है: " + shorten(english) + ".";
    }

    /** Explain a Hindi screen/sentence into English. */
    public String explainToEnglish(String hindi) {
        if (hindi == null || hindi.trim().isEmpty()) return "";
        String lower = hindi.toLowerCase(Locale.US).trim();
        if (lower.contains("error") || lower.contains("त्रुटि") || lower.contains("गलती")) {
            return "Sir, this is an error message.";
        }
        if (lower.contains("सफल") || lower.contains("पूरा")) {
            return "Sir, this says the task is complete.";
        }
        if (lower.contains("आपका स्वागत") || lower.contains("स्वागत")) {
            return "Sir, this means welcome.";
        }
        return "Sir, on screen it shows: " + shorten(hindi) + ".";
    }

    /**
     * Translation dispatch used by subtitle/live pipeline.
     * @param text    the visible text.
     * @param target  "hi" or "en" target explanation.
     */
    public String translate(String text, String target) {
        if (text == null || text.trim().isEmpty()) return "";
        String src = detect(text);
        if ("hi".equals(target)) {
            if ("hi".equals(src)) return shorten(text);       // already Hindi
            return explainToHindi(text);
        }
        if ("en".equals(target)) {
            if ("en".equals(src)) return shorten(text);       // already English
            return explainToEnglish(text);
        }
        // AUTO target
        if ("hi".equals(src)) return explainToEnglish(text);
        return explainToHindi(text);
    }

    private String shorten(String s) {
        String c = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        return c.length() > 120 ? c.substring(0, 120) + "…" : c;
    }

    public boolean isSubtitleEnabled() {
        String mode = settings.getSubtitleMode();
        return !"off".equals(mode);
    }
}
