package com.afroz.voicebubble.chat;

import com.afroz.voicebubble.speech.TtsEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JARVIS conversation engine.
 *
 * A lightweight, fully offline rule-based NLU that provides:
 *  - Bilingual input handling (Hindi / English) with script-based language
 *    detection and dynamic TTS output routing.
 *  - Two-way Q&amp;A: an interactive state machine that asks proactive
 *    clarification questions (e.g. about an ambiguous terminal error) and
 *    keeps an active-listening session to receive the user's confirmation.
 *  - Screen context comprehension: a rolling snapshot of the last scraped
 *    screen text lets "यह एरर" / "this issue" resolve without re-reading the
 *    whole screen.
 */
public class JarvisBrain {

    /** Latest screen context snapshot, updated by the accessibility service. */
    private volatile String screenContext = "";
    private volatile boolean hasScreenContext = false;

    /** The most recently discussed terminal error/fix, for clarification. */
    private volatile String pendingError = "";
    private volatile String pendingFix = "";

    /** Active conversation state. */
    private enum State { IDLE, AWAITING_FIX_CHOICE, AWAITING_EXPLAIN_CHOICE }
    private State state = State.IDLE;

    /** An error that is ambiguous / has multiple possible fixes. */
    private volatile ErrorSituation pendingSituation;

    // Bilingual template phrases (kept here so the engine is self-contained).
    private static final Map<String, String> HI = new LinkedHashMap<>();
    private static final Map<String, String> EN = new LinkedHashMap<>();

    static {
        // Wake trigger reply (deep male tone, follow-up kept listening).
        HI.put("wake", "जी अफ़रोज़ सर, बताइए क्या हुक्म है?");
        EN.put("wake", "Yes sir, I am listening. What shall I check?");

        HI.put("greet", "नमस्ते अफ़रोज़ सर! मैं जार्विस। क्या करूँ आपके लिए?");
        EN.put("greet", "Hello Afroz sir! I am JARVIS. What may I do for you?");

        HI.put("howareyou", "मैं बिल्कुल ठीक हूँ सर, आपकी सेवा में। आप कैसे हैं?");
        EN.put("howareyou", "I'm doing great sir, at your service. How are you?");

        HI.put("whoareyou", "मैं जार्विस हूँ, आपका ऑफलाइन वॉयस असिस्टेंट। मैं स्क्रीन पढ़ सकता हूँ, टर्मिनल की त्रुटियाँ समझा सकता हूँ, और हिंदी तथा अंग्रेजी दोनों में बात कर सकता हूँ।");
        EN.put("whoareyou", "I am JARVIS, your offline voice assistant. I can read the screen, explain terminal errors, and speak both Hindi and English.");

        HI.put("help", "आप मुझसे कह सकते हैं: \"स्क्रीन पढ़ो\", \"यह एरर समझाओ\", या मुझसे सवाल पूछ सकते हैं। मैं हिंदी और अंग्रेजी दोनों समझता हूँ।");
        EN.put("help", "You can ask me to \"Read Screen\", \"Explain this error\", or just ask me a question. I understand both Hindi and English.");

        HI.put("thanks", "आपका स्वागत है सर! और कुछ मदद चाहिए?");
        EN.put("thanks", "You're welcome sir! Anything else I can help with?");

        HI.put("readscreen_done", "स्क्रीन पढ़ दी गई है।");
        EN.put("readscreen_done", "Screen has been read.");

        HI.put("no_text", "स्क्रीन पर कोई टेक्स्ट नहीं मिला।");
        EN.put("no_text", "No text found on the screen.");

        HI.put("yes_to_fix", "ठीक है सर, मैं फिक्स कमांड समझाता हूँ। ");
        EN.put("yes_to_fix", "Very well sir, here is the fix command. ");

        HI.put("no_to_fix", "ठीक है सर, कोई बात नहीं। मैं सिर्फ़ एरर समझा रहा हूँ। ");
        EN.put("no_to_fix", "Alright sir, no problem. I am just explaining the error. ");

        HI.put("first_explain", "ठीक है, मैं पहली समस्या समझाता हूँ। ");
        EN.put("first_explain", "Very well, I will explain the first one. ");

        HI.put("complete_fix", "ठीक है, मैं पूरा फिक्स कमांड देता हूँ। ");
        EN.put("complete_fix", "Understood, here is the complete fix command. ");

        HI.put("unknown", "माफ़ कीजिए, मैं समझ नहीं पाया। क्या आप कृपया दोहराएँगे? आप कह सकते हैं \"स्क्रीन पढ़ो\" या \"एरर समझाओ\"।");
        EN.put("unknown", "Sorry, I did not understand that. Could you please repeat? You can say \"Read Screen\" or \"Explain error\".");
    }

    // ============================================================
    // Screen context comprehension
    // ============================================================

    /** Update the latest screen snapshot (called by the reader service). */
    public void setScreenContext(String text) {
        this.screenContext = text == null ? "" : text;
        this.hasScreenContext = !this.screenContext.trim().isEmpty();
    }

    public String getScreenContext() {
        return screenContext;
    }

    public boolean hasScreenContext() {
        return hasScreenContext;
    }

    /**
     * Resolve a screen reference ("this issue", "यह एरर") against the current
     * snapshot without re-reading the whole screen.
     */
    public String resolveScreenReference() {
        return hasScreenContext ? screenContext : "";
    }

    // ============================================================
    // Terminal-error clarification state
    // ============================================================

    /** A terminal error with an optional proactive clarification question. */
    public static class ErrorSituation {
        public final String error;
        public final String fix;
        public final boolean ambiguous;

        public ErrorSituation(String error, String fix, boolean ambiguous) {
            this.error = error;
            this.fix = fix;
            this.ambiguous = ambiguous;
        }
    }

    /**
     * Register a detected terminal error. If ambiguous, JARVIS asks a
     * bilingual follow-up question and waits for the user's confirmation
     * (proactive clarification). Otherwise it answers immediately.
     */
    public JarvisReply handleTerminalError(ErrorSituation situation, String lang) {
        this.pendingError = situation.error;
        this.pendingFix = situation.fix;

        if (situation.ambiguous) {
            this.pendingSituation = situation;
            this.state = State.AWAITING_FIX_CHOICE;
            if ("hi".equals(lang)) {
                String q = "सर, यह एरर परमिशन या टूटी डिपेंडेंसी से संबंधित लगता है। "
                        + "क्या आप फिक्स कमांड चाहते हैं, या सिर्फ़ एरर की व्याख्या?";
                return new JarvisReply(q, "hi", q, "hi", true);
            } else {
                String q = "Sir, this error looks related to permissions or a broken dependency. "
                        + "Would you like the fix command, or just an explanation?";
                return new JarvisReply(q, "en", q, "en", true);
            }
        }

        this.state = State.IDLE;
        return new JarvisReply(situation.fix, lang);
    }

    // ============================================================
    // Main entry: handle a user utterance and produce a reply.
    // ============================================================

    /**
     * Process the user's spoken text and return JARVIS's reply.
     *
     * @param text   the recognized utterance.
     * @param lang   the language the utterance was voiced in ("hi"/"en"),
     *               detected by the recognizer / script.
     */
    public JarvisReply respond(String text, String lang) {
        if (text == null || text.trim().isEmpty()) {
            return new JarvisReply(lang == "hi" ? HI.get("unknown") : EN.get("unknown"),
                    "hi".equals(lang) ? "hi" : "en");
        }

        // Don't trust the passed lang blindly; re-detect by script too.
        String detected = TtsEngine.detectLang(text);
        final String L = "hi".equals(detected) ? "hi" : "en";

        // Clarification sub-dialog: the user was asked a question.
        if (state == State.AWAITING_FIX_CHOICE && pendingSituation != null) {
            return handleFixChoice(text, L);
        }
        if (state == State.AWAITING_EXPLAIN_CHOICE) {
            return handleExplainChoice(text, L);
        }

        String lower = text.toLowerCase(Locale.US).trim();

        // Greetings & small talk.
        if (matchesAny(lower, "namaste", "नमस्ते", "hello", "hi ", "hey", "good morning",
                "good evening", "good afternoon", "salaam", "शुभ")) {
            return phrase("greet", L);
        }
        if (matchesAny(lower, "how are you", "kaise ho", "कैसे हो", "कैसा", "how do you do")) {
            return phrase("howareyou", L);
        }
        if (matchesAny(lower, "who are you", "tum kaun", "kaun ho", "तुम कौन", "आप कौन")) {
            return phrase("whoareyou", L);
        }
        if (matchesAny(lower, "help", "madad", "मदद", "what can you do")) {
            return phrase("help", L);
        }
        if (matchesAny(lower, "thank", "dhanyavad", "शुक्रिया", "thankyou", "thanks")) {
            return phrase("thanks", L);
        }

        // Screen reading.
        if (matchesAny(lower, "read screen", "screen padho", "स्क्रीन पढ़ो", "read the screen",
                "screen read", "padho screen", "पढ़ो")) {
            if (hasScreenContext) {
                // Respond in the user's language, but read the scraped text
                // in English TTS (screen content is typically English).
                return new JarvisReply(phraseText("readscreen_done", L)
                        + " " + screenContext, L);
            }
            return phrase("no_text", L);
        }

        // Terminal error help via screen context.
        if (matchesAny(lower, "explain error", "error samjhao", "एरर समझाओ", "what is this error",
                "this error", "error kya hai", "यह एरर", "bug samjhao", "bug kya",
                "how to fix", "kaise fix", "कैसे फिक्स", "fix hoga")) {
            String ctx = resolveScreenReference();
            if (!ctx.isEmpty()) {
                return explainFromScreen(ctx, L);
            }
            return new JarvisReply(
                    "Could you open the terminal first so I can see the error?",
                    "en", null, "en", false);
        }

        // "Is this correct?" — validate the command / code currently on screen.
        if (matchesAny(lower, "sahi hai kya", "सही है", "सही है क्या", "is this correct",
                "is this right", "theek hai kya", "ठीक है क्या", "is correct")) {
            return evaluateScreenForCorrectness(L);
        }

        // "What is happening / what's running?" — summarize the current screen.
        if (matchesAny(lower, "kya chal raha", "क्या चल", "what is happening", "whats happening",
                "what is going", "kya ho raha", "क्या हो", "what is going on", "kya chal raha hai",
                "क्या चल रहा")) {
            return summarizeScreen(L);
        }

        // Time / generic date fallback.
        if (matchesAny(lower, "time kya", "time ", "कितने बजे")) {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("hh:mm a");
            String t = fmt.format(new java.util.Date());
            String hiT = "अभी समय है " + t + "।";
            String enT = "The current time is " + t + ".";
            return new JarvisReply("hi".equals(L) ? hiT : enT, L);
        }

        // Fallback.
        return phrase("unknown", L);
    }

    /** Handle the user's answer to the proactive permission/dependency question. */
    private JarvisReply handleFixChoice(String text, String L) {
        String lower = text.toLowerCase(Locale.US).trim();
        ErrorSituation s = this.pendingSituation;

        if (isAffirmative(lower)) {
            this.state = State.IDLE;
            this.pendingSituation = null;
            String pre = ("hi".equals(L) ? HI.get("yes_to_fix") : EN.get("yes_to_fix"));
            return new JarvisReply(pre + s.fix, L);
        } else if (isNegative(lower)) {
            this.state = State.IDLE;
            this.pendingSituation = null;
            String pre = ("hi".equals(L) ? HI.get("no_to_fix") : EN.get("no_to_fix"));
            return new JarvisReply(pre + s.error + " : " + s.fix, L);
        }
        // Unexpected answer: repeat the clarification.
        return new JarvisReply(("hi".equals(L) ? HI.get("unknown") : EN.get("unknown"))
                + " " + (("hi".equals(L) ? "फिक्स कमांड चाहिए? हाँ या नहीं।" : "Would you like the fix command? Yes or no.")),
                L, null, L, true);
    }

    /** Handle the user's answer to the "explain first or full fix" question. */
    private JarvisReply handleExplainChoice(String text, String L) {
        String lower = text.toLowerCase(Locale.US).trim();
        this.state = State.IDLE;
        String pre = ("hi".equals(L) ? HI.get("complete_fix") : EN.get("complete_fix"));
        if (matchesAny(lower, "complete", "full fix", "fix it", "fix command", "poora", "पूरा", "fix")) {
            return new JarvisReply(pre + (pendingFix.isEmpty() ? "" : pendingFix), L);
        }
        String pre1 = ("hi".equals(L) ? HI.get("first_explain") : EN.get("first_explain"));
        return new JarvisReply(pre1 + (pendingFix.isEmpty() ? "" : pendingFix), L);
    }

    /**
     * Build a terminal-error explanation from a screen-context string.
     * Demonstrates proactive clarification for ambiguous/multi-issue screens.
     */
    private JarvisReply explainFromScreen(String ctx, String L) {
        List<String> issues = new ArrayList<>();
        String lower = ctx.toLowerCase(Locale.US);
        if (lower.contains("permission denied")) issues.add("permission denied");
        if (lower.contains("command not found")) issues.add("command not found");
        if (lower.contains("no such file")) issues.add("file not found");
        if (lower.contains("module not found") || lower.contains("importerror"))
            issues.add("missing module / import");
        if (lower.contains("no space left")) issues.add("no disk space");
        if (lower.contains("connection refused") || lower.contains("could not resolve"))
            issues.add("network / connection");

        if (issues.isEmpty()) {
            if ("hi".equals(L)) {
                return new JarvisReply("मुझे स्क्रीन पर कोई स्पष्ट एरर नहीं दिख रहा। कृपया कुछ कोड या एरर मैसेज दिखाइए।", "hi");
            }
            return new JarvisReply("I don't see a clear error on the screen. Please show me some code or an error message.", "en");
        }

        if (issues.size() > 1) {
            // Ambiguous: multiple issues. Ask proactively which to address.
            this.pendingFix = "Run step by step. First fix \"" + issues.get(0)
                    + "\", then re-run and I will help with the next one.";
            this.state = State.AWAITING_EXPLAIN_CHOICE;
            if ("hi".equals(L)) {
                String q = "सर, स्क्रीन पर कई समस्याएँ मिलीं - जैसे " + String.join(", ", issues)
                        + "। क्या मैं पहली समस्या समझाऊँ, या पूरा फिक्स कमांड दूँ?";
                return new JarvisReply(q, "hi", q, "hi", true);
            } else {
                String q = "Sir, I found multiple issues on screen - like " + String.join(", ", issues)
                        + ". Should I explain the first one, or give the complete fix command?";
                return new JarvisReply(q, "en", q, "en", true);
            }
        }

        // Single, unambiguous issue: answer directly, in the user's language.
        String fix = fixFor(issues.get(0), L);
        if ("hi".equals(L)) {
            return new JarvisReply("यह एरर \"" + issues.get(0) + "\" है। " + fix, "hi");
        }
        return new JarvisReply("This is a \"" + issues.get(0) + "\" error. " + fix, "en");
    }

    /**
     * "Is this correct?" — inspect the latest command/code on screen and give
     * a clean verdict, never a raw transcription.
     */
    private JarvisReply evaluateScreenForCorrectness(String L) {
        String ctx = resolveScreenReference();
        if (ctx.isEmpty()) {
            return new JarvisReply(
                    "hi".equals(L) ? "सर, फ़िलहाल स्क्रीन पर कुछ नहीं दिख रहा जिसे जाँचा जा सके।"
                                   : "Sir, there's nothing on screen right now for me to check.",
                    L);
        }
        String lower = ctx.toLowerCase(Locale.US);
        boolean hasError = lower.contains("error") || lower.contains("exception")
                || lower.contains("failed") || lower.contains("not found")
                || lower.contains("permission denied") || lower.contains("command not found");
        if (hasError) {
            return new JarvisReply(
                    "hi".equals(L)
                        ? "सर, यहाँ एक एरर है, यह बिल्कुल सही नहीं है। "
                            + fixFor(firstIssue(ctx), L)
                        : "Sir, there is an error here, so this is not quite correct. "
                            + fixFor(firstIssue(ctx), L),
                    L);
        }
        return new JarvisReply(
                "hi".equals(L)
                    ? "हाँ सर, स्क्रीन पर जो दिख रहा है वह बिल्कुल सही लगता है। यह कमांड रन कर दीजिए।"
                    : "Yes sir, what is on the screen looks correct. Please go ahead and run the command.",
                L);
    }

    /** Summarize what is currently happening on screen (no raw log dump). */
    private JarvisReply summarizeScreen(String L) {
        String ctx = resolveScreenReference();
        if (ctx.isEmpty()) {
            return new JarvisReply(
                    "hi".equals(L) ? "सर, फ़िलहाल स्क्रीन पर कुछ नहीं चल रहा दिखता।"
                                   : "Sir, nothing appears to be running on screen right now.",
                    L);
        }
        List<String> issues = new ArrayList<>();
        String lower = ctx.toLowerCase(Locale.US);
        if (lower.contains("error") || lower.contains("exception")) issues.add("एक एरर आया है");
        if (lower.contains("running") || lower.contains("progress") || lower.contains("installing"))
            issues.add("कोई कार्य चल रहा है");
        if (lower.contains("done") || lower.contains("success")) issues.add("कार्य पूर्ण हुआ");
        if (issues.isEmpty()) issues.add("एक टर्मिनल स्क्रीन खुली है");

        String joined;
        boolean hi = "hi".equals(L);
        if (hi) {
            joined = String.join(", ", issues);
            return new JarvisReply("सर, अभी " + joined + "।", L);
        } else {
            joined = issues.get(0);
            return new JarvisReply("Sir, right now " + joined + ".", L);
        }
    }

    private String firstIssue(String ctx) {
        String lower = ctx.toLowerCase(Locale.US);
        if (lower.contains("permission denied")) return "permission denied";
        if (lower.contains("command not found")) return "command not found";
        if (lower.contains("no such file")) return "file not found";
        if (lower.contains("no space left")) return "no disk space";
        if (lower.contains("connection refused") || lower.contains("could not resolve"))
            return "network / connection";
        return "error";
    }

    private String fixFor(String issue, String L) {
        boolean hi = "hi".equals(L);
        switch (issue) {
            case "permission denied":
                return hi ? "फिक्स: \"chmod 755 फाइल नाम\" चलाएँ, और रूट की ज़रूरत हो तो \"su\" उपयोग करें।"
                          : "Fix: run \"chmod 755 filename\", or use \"su\" if root is required.";
            case "command not found":
                return hi ? "फिक्स: \"pkg install कमांड-नाम\" चलाएँ, जैसे \"pkg install git\"।"
                          : "Fix: run \"pkg install command-name\", e.g. \"pkg install git\".";
            case "file not found":
                return hi ? "फिक्स: \"pwd\" और \"ls\" से अपना पाथ जाँचें और सही पाथ उपयोग करें।"
                          : "Fix: check your path with \"pwd\" and \"ls\", then use the correct one.";
            case "missing module / import":
                return hi ? "फिक्स: \"pip install मॉड्यूल\" चलाएँ।"
                          : "Fix: run \"pip install module\".";
            case "no disk space":
                return hi ? "फिक्स: \"df -h\" से जाँचें और \"apt autoremove\" चलाएँ।"
                          : "Fix: check with \"df -h\", then run \"apt autoremove\".";
            case "network / connection":
                return hi ? "फिक्स: इंटरनेट जाँचें या DNS के लिए \"termux-change-repo\" चलाएँ।"
                          : "Fix: check your internet or run \"termux-change-repo\".";
            default:
                return hi ? "कृपया एरर मैसेज ध्यान से पढ़ें।" : "Please read the error message carefully.";
        }
    }

    // ============================================================
    // Wake-word handling
    // ============================================================

    /** True if the utterance is a JARVIS wake trigger (Hindi or English). */
    public boolean isWakeWord(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.US).trim().replaceAll("[!.,?]", "");
        return t.contains("jarvis") || t.contains("जार्विस") || t.contains("जारविस");
    }

    /**
     * Reply used the moment a wake word is detected. Response is voiced in the
     * detected language via the active (deep male) voice profile.
     */
    public JarvisReply respondWake(String lang) {
        return new JarvisReply(
                "hi".equals(lang) ? HI.get("wake") : EN.get("wake"),
                "hi".equals(lang) ? "hi" : "en");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private JarvisReply phrase(String key, String lang) {
        return new JarvisReply(phraseText(key, lang), lang);
    }

    private String phraseText(String key, String lang) {
        if ("hi".equals(lang)) return HI.get(key);
        return EN.get(key);
    }

    private boolean isAffirmative(String lower) {
        return matchesAny(lower, "yes", "haan", "हाँ", "hmm", "fix", "thik hai", "ठीक है",
                "okay", "ok", "sure", "karo", "करो", "ha", "ha ji", "हा");
    }

    private boolean isNegative(String lower) {
        return matchesAny(lower, "no", "nahi", "नहीं", "nope", "na", "bus", "बस",
                "no need", "mat karo", "मत करो");
    }

    private boolean matchesAny(String lower, String... needles) {
        for (String n : needles) {
            if (lower.startsWith(n) || lower.contains(n)) return true;
        }
        return false;
    }
}
