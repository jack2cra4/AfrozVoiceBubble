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

    public LanguageMode getLanguageMode() {
        String v = prefs.getString(KEY_LANG, "AUTO");
        try {
            return LanguageMode.valueOf(v);
        } catch (Exception e) {
            return LanguageMode.AUTO;
        }
    }

    public void setLanguageMode(LanguageMode mode) {
        prefs.edit().putString(KEY_LANG, mode.name()).apply();
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
