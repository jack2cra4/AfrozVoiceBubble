package com.afroz.voicebubble.reader;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.speech.TtsEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AccessibilityService that reads on-screen text, subtitles and Termux terminal
 * errors aloud in Hindi. Detection is enabled/disabled by toggling the bubble.
 */
public class ScreenReaderAccessibilityService extends AccessibilityService {

    public static final String PREFS = "reader_prefs";
    public static final String KEY_ENABLED = "screen_reader_enabled";

    private static final String TERMUX_PACKAGE = "com.termux";
    private static final long SPEAK_COOLDOWN_MS = 5000;

    private TermuxErrorHelper termuxHelper;
    private String lastSpoken = "";
    private long lastSpeakTime = 0;
    private String currentPackage = "";
    private boolean readerToggledOn = false;

    // English -> Hindi dictionary for common UI / subtitle words.
    private static final Map<String, String> EN_HI = new HashMap<>();
    static {
        EN_HI.put("subscribe", "सब्सक्राइब करें");
        EN_HI.put("like", "लाइक करें");
        EN_HI.put("comment", "कमेंट करें");
        EN_HI.put("share", "शेयर करें");
        EN_HI.put("settings", "सेटिंग्स");
        EN_HI.put("save", "सेव करें");
        EN_HI.put("cancel", "रद्द करें");
        EN_HI.put("delete", "हटाएं");
        EN_HI.put("search", "खोजें");
        EN_HI.put("loading", "लोड हो रहा है");
        EN_HI.put("error", "त्रुटि");
        EN_HI.put("success", "सफल");
        EN_HI.put("warning", "चेतावनी");
        EN_HI.put("network error", "नेटवर्क त्रुटि");
        EN_HI.put("connection failed", "कनेक्शन विफल");
        EN_HI.put("no internet", "इंटरनेट नहीं है");
        EN_HI.put("please wait", "कृपया प्रतीक्षा करें");
        EN_HI.put("try again", "फिर से कोशिश करें");
        EN_HI.put("close", "बंद करें");
        EN_HI.put("open", "खोलें");
        EN_HI.put("next", "अगला");
        EN_HI.put("previous", "पिछला");
        EN_HI.put("send", "भेजें");
        EN_HI.put("download", "डाउनलोड");
        EN_HI.put("upload", "अपलोड");
        EN_HI.put("play", "चलाएं");
        EN_HI.put("pause", "रोकें");
        EN_HI.put("stop", "रोकें");
        EN_HI.put("mute", "म्यूट");
        EN_HI.put("home", "होम");
        EN_HI.put("back", "वापस");
        EN_HI.put("profile", "प्रोफ़ाइल");
        EN_HI.put("confirm", "पुष्टि करें");
        EN_HI.put("enable", "सक्षम करें");
        EN_HI.put("disable", "अक्षम करें");
        EN_HI.put("yes", "हाँ");
        EN_HI.put("no", "नहीं");
        EN_HI.put("ok", "ठीक है");
        EN_HI.put("done", "हो गया");
        EN_HI.put("start", "शुरू करें");
        EN_HI.put("submit", "जमा करें");
        EN_HI.put("update", "अपडेट करें");
        EN_HI.put("retry", "फिर से कोशिश करें");
        EN_HI.put("subtitles", "उपशीर्षक");
        EN_HI.put("captions", "कैप्शन");
        EN_HI.put("translate", "अनुवाद");
        EN_HI.put("copy", "कॉपी करें");
        EN_HI.put("paste", "पेस्ट करें");
        EN_HI.put("view", "देखें");
        EN_HI.put("edit", "संपादित करें");
        EN_HI.put("add", "जोड़ें");
        EN_HI.put("remove", "हटाएं");
        EN_HI.put("more", "और");
        EN_HI.put("skip", "छोड़ें");
        EN_HI.put("continue", "जारी रखें");
        EN_HI.put("install", "इंस्टॉल करें");
        EN_HI.put("uninstall", "अनइंस्टॉल करें");
        EN_HI.put("logout", "लॉग आउट");
        EN_HI.put("login", "लॉग इन");
        EN_HI.put("help", "मदद");
        EN_HI.put("loading failed", "लोड विफल");
    }

    public static void setScreenReaderEnabled(boolean enabled) {
        Context app = App.get();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
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

            // Termux error detection always active while Termux is foreground.
            boolean inTermux = TERMUX_PACKAGE.equals(currentPackage);
            if (inTermux) {
                String termuxText = extractAllText(root);
                if (termuxHelper == null) {
                    termuxHelper = new TermuxErrorHelper(this);
                }
                termuxHelper.analyze(termuxText);
            }

            // Only read on-screen text if the user toggled the reader on.
            if (!isReaderEnabled()) {
                root.recycle();
                return;
            }

            String combined = extractVisibleText(root);
            root.recycle();

            if (combined.isEmpty()) return;
            if (combined.equals(lastSpoken)) return;
            long now = System.currentTimeMillis();
            if (now - lastSpeakTime < SPEAK_COOLDOWN_MS) return;

            lastSpoken = combined;
            lastSpeakTime = now;

            String hindi = translate(combined);
            TtsEngine tts = App.get().getTts();
            if (inTermux) {
                tts.speak("टर्मिनल: " + combined, false);
            } else if (hindi != null) {
                tts.speak(hindi, false);
            } else {
                tts.speak(combined, false);
            }
        } catch (Exception e) {
            // never crash the service
        }
    }

    private boolean isReaderEnabled() {
        return App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }

    /**
     * Collect visible text and content descriptions from the node tree.
     */
    private String extractVisibleText(AccessibilityNodeInfo root) {
        List<String> texts = new ArrayList<>();
        collectVisible(root, texts);
        StringBuilder sb = new StringBuilder();
        if (!texts.isEmpty()) {
            for (String t : texts) {
                if (sb.length() < 400) {
                    sb.append(t).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }

    private void collectVisible(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        try {
            if (node.isVisibleToUser()) {
                CharSequence text = node.getText();
                if (text != null) {
                    String s = text.toString().trim();
                    if (s.length() > 1) out.add(s);
                }
                CharSequence desc = node.getContentDescription();
                if (desc != null) {
                    String s = desc.toString().trim();
                    if (s.length() > 1) out.add(s);
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                collectVisible(node.getChild(i), out);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Raw dump of every text node, used for Termux error scanning.
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

    /**
     * Translate a detected English phrase to Hindi. Returns null if no match.
     */
    private String translate(String english) {
        if (english == null) return null;
        String trimmed = english.trim();
        if (EN_HI.containsKey(trimmed.toLowerCase(Locale.US))) {
            return EN_HI.get(trimmed.toLowerCase(Locale.US));
        }
        for (Map.Entry<String, String> e : EN_HI.entrySet()) {
            if (trimmed.toLowerCase(Locale.US).contains(e.getKey())) {
                return e.getValue();
            }
        }
        // Fallback: preface English sentences with a Hindi marker.
        if (isEnglishSentence(trimmed)) {
            return "अनुवाद: " + trimmed;
        }
        return null;
    }

    private boolean isEnglishSentence(String text) {
        if (text.length() > 200) return false;
        int letters = 0;
        for (char c : text.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == ' ') {
                letters++;
            }
        }
        if (text.isEmpty()) return false;
        float ratio = (float) letters / text.length();
        return ratio > 0.7f;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (termuxHelper != null) {
            termuxHelper.destroy();
        }
    }
}
