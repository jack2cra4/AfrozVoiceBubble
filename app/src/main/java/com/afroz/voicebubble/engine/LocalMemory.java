package com.afroz.voicebubble.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Locally persisted JARVIS settings (user name, language mode, voice profile,
 * mute). All values stay on-device; nothing is uploaded. This is the single
 * source of truth for assistant configuration so voice/language changes apply
 * live without restarting.
 */
public class LocalMemory {

    public enum LanguageMode { HINDI, ENGLISH, AUTO }

    private static final String PREFS = "jarvis_settings";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_LANG = "lang_mode";
    private static final String KEY_MALE = "voice_male";
    private static final String KEY_MUTE = "muted";

    private final SharedPreferences prefs;

    public LocalMemory(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getUserName() {
        String name = prefs.getString(KEY_NAME, "");
        return name == null || name.trim().isEmpty() ? "अफ़रोज़" : name.trim();
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_NAME, name == null ? "" : name.trim()).apply();
    }

    /**
     * Language mode read in the shared "hi"/"en"/"auto" encoding so it stays
     * consistent with {@link SettingsManager} (same prefs file). Older enum
     * values ("HINDI"/"ENGLISH"/"AUTO") are mapped for backward compatibility.
     */
    public LanguageMode getLanguageMode() {
        String v = prefs.getString(KEY_LANG, "auto");
        if (v == null) return LanguageMode.AUTO;
        switch (v.toLowerCase()) {
            case "hi":
            case "hindi":
                return LanguageMode.HINDI;
            case "en":
            case "english":
                return LanguageMode.ENGLISH;
            case "auto":
            default:
                return LanguageMode.AUTO;
        }
    }

    public void setLanguageMode(LanguageMode mode) {
        String v;
        switch (mode) {
            case HINDI: v = "hi"; break;
            case ENGLISH: v = "en"; break;
            default: v = "auto"; break;
        }
        prefs.edit().putString(KEY_LANG, v).apply();
    }

    public boolean isMaleVoice() {
        return prefs.getBoolean(KEY_MALE, true);
    }

    public void setMaleVoice(boolean male) {
        prefs.edit().putBoolean(KEY_MALE, male).apply();
    }

    public boolean isMuted() {
        return prefs.getBoolean(KEY_MUTE, false);
    }

    public void setMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MUTE, muted).apply();
    }
}
