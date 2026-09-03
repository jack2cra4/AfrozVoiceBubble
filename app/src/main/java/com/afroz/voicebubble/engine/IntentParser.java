package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Parses a user's spoken utterance into a structured intent using bilingual
 * (Hindi + English) keyword matching. This is the offline "intent parser" of
 * the local understanding layer.
 */
public class IntentParser {

    public enum Intent {
        READ_SCREEN, WHAT_ON_SCREEN, EXPLAIN_ERROR, HOW_TO_FIX,
        WHAT_IS_RUNNING, TIME_REMAINING, IS_CORRECT, NEXT_STEP,
        GREETING, WAKE, STOP_ASSISTANT, UNKNOWN, ASK_JARVIS
    }

    public static final class ParsedIntent {
        public final Intent intent;
        public final String raw;

        ParsedIntent(Intent intent, String raw) {
            this.intent = intent;
            this.raw = raw;
        }
    }

    public ParsedIntent parse(String text) {
        if (text == null) {
            return new ParsedIntent(Intent.UNKNOWN, "");
        }
        String lower = text.toLowerCase(Locale.US).trim();
        String norm = lower.replaceAll("[!.,?]", "");

        if (containsAny(norm,
                "jarvis", "जार्विस", "जारविस", "hello", "हेलो")) {
            if (containsAny(norm, "jarvis", "जार्विस", "जारविस")) {
                return new ParsedIntent(Intent.WAKE, text);
            }
        }

        if (containsAny(norm,
                "screen padho", "स्क्रीन पढ़ो", "read screen", "read the screen",
                "screen par kya", "स्क्रीन पर क्या", "padho", "देखो")) {
            return new ParsedIntent(Intent.READ_SCREEN, text);
        }
        if (containsAny(norm,
                "what on screen", "screen par kya hai", "kya dikh raha",
                "क्या दिख", "screen me kya")) {
            return new ParsedIntent(Intent.WHAT_ON_SCREEN, text);
        }
        if (containsAny(norm,
                "error samjhao", "एरर समझाओ", "explain error", "this error",
                "error kya hai", "यह एरर", "bug kya", "kya hua", "क्या हुआ",
                "why error", "error kyon", "एरर क्यों")) {
            return new ParsedIntent(Intent.EXPLAIN_ERROR, text);
        }
        if (containsAny(norm,
                "how to fix", "kaise fix", "कैसे फिक्स", "fix hoga", "fix kar",
                "इसे कैसे फिक्स", "command batao", "कमांड बताओ", "kya karu",
                "अब क्या")) {
            return new ParsedIntent(Intent.HOW_TO_FIX, text);
        }
        if (containsAny(norm,
                "kya chal raha", "क्या चल", "what is happening", "whats happening",
                "what is running", "kya ho raha", "क्या हो")) {
            return new ParsedIntent(Intent.WHAT_IS_RUNNING, text);
        }
        if (containsAny(norm,
                "time remaining", "kitna time", "कितना समय", "कितना टाइम",
                "kitni der", "बाकी कितना", "how long left", "percent")) {
            return new ParsedIntent(Intent.TIME_REMAINING, text);
        }
        if (containsAny(norm,
                "sahi hai", "सही है", "is this correct", "is this right",
                "theek hai", "ठीक है क्या", "command sahi")) {
            return new ParsedIntent(Intent.IS_CORRECT, text);
        }
        if (containsAny(norm,
                "next step", "agla kya", "अगला क्या", "next kya", "abb kya",
                "आगे क्या")) {
            return new ParsedIntent(Intent.NEXT_STEP, text);
        }
        if (containsAny(norm, "namaste", "नमस्ते", "good morning", "shubh", "शुभ")) {
            return new ParsedIntent(Intent.GREETING, text);
        }
        if (containsAny(norm, "stop", "रुको", "shut down", "band karo", "बंद")) {
            return new ParsedIntent(Intent.STOP_ASSISTANT, text);
        }

        return new ParsedIntent(Intent.UNKNOWN, text);
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }
}
