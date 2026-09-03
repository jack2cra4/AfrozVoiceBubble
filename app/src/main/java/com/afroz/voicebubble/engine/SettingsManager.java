package com.afroz.voicebubble.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Centralised, persisted JARVIS configuration &amp; behaviour switches.
 *
 * This is the single source of truth for every user setting (auto-start, wake
 * word, proactive assistant, live-screen options, subtitle modes, translation,
 * privacy, performance). All values stay on-device. The segmented overrides
 * let the UI bind to simple boolean/int switches without touching the raw
 * {@link SharedPreferences}.
 */
public class SettingsManager {

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE);
    }

    // ---- GENERAL -----------------------------------------------------------
    public boolean isAutoStart() { return prefs.getBoolean("auto_start", false); }
    public void setAutoStart(boolean v) { prefs.edit().putBoolean("auto_start", v).apply(); }

    public boolean isWakeWordEnabled() { return prefs.getBoolean("wake_word", true); }
    public void setWakeWordEnabled(boolean v) { prefs.edit().putBoolean("wake_word", v).apply(); }

    public boolean isProactive() { return prefs.getBoolean("proactive", true); }
    public void setProactive(boolean v) { prefs.edit().putBoolean("proactive", v).apply(); }

    // ---- VOICE -------------------------------------------------------------
    public boolean isMaleVoice() { return prefs.getBoolean("voice_male", true); }
    public void setMaleVoice(boolean v) { prefs.edit().putBoolean("voice_male", v).apply(); }

    public float getSpeechRate() { return prefs.getFloat("speech_rate", 1.0f); }
    public void setSpeechRate(float v) { prefs.edit().putFloat("speech_rate", v).apply(); }

    // ---- LANGUAGE ----------------------------------------------------------
    /** "hi" / "en" / "auto" */
    public String getLanguageMode() { return prefs.getString("lang_mode", "auto"); }
    public void setLanguageMode(String v) { prefs.edit().putString("lang_mode", v).apply(); }

    // ---- LIVE SCREEN -------------------------------------------------------
    public boolean isScreenMonitoring() { return prefs.getBoolean("screen_monitoring", true); }
    public void setScreenMonitoring(boolean v) { prefs.edit().putBoolean("screen_monitoring", v).apply(); }

    /** OCR frequency in ms (performance tuning). */
    public int getOcrFrequency() { return prefs.getInt("ocr_frequency", 1200); }
    public void setOcrFrequency(int v) { prefs.edit().putInt("ocr_frequency", v).apply(); }

    /** Change-detection sensitivity (higher = detect smaller changes). */
    public int getChangeSensitivity() { return prefs.getInt("change_sensitivity", 2); }
    public void setChangeSensitivity(int v) { prefs.edit().putInt("change_sensitivity", v).apply(); }

    // ---- SUBTITLES ---------------------------------------------------------
    /** "off" / "subtitles_only" / "important_events" / "full_live" */
    public String getSubtitleMode() { return prefs.getString("subtitle_mode", "off"); }
    public void setSubtitleMode(String v) { prefs.edit().putString("subtitle_mode", v).apply(); }

    public boolean isTranslateSubtitles() { return prefs.getBoolean("translate_subtitles", true); }
    public void setTranslateSubtitles(boolean v) { prefs.edit().putBoolean("translate_subtitles", v).apply(); }

    public boolean isSpeakTranslation() { return prefs.getBoolean("speak_translation", true); }
    public void setSpeakTranslation(boolean v) { prefs.edit().putBoolean("speak_translation", v).apply(); }

    // ---- PRIVACY -----------------------------------------------------------
    public boolean isLocalOnly() { return prefs.getBoolean("local_only", true); }
    public void setLocalOnly(boolean v) { prefs.edit().putBoolean("local_only", v).apply(); }

    // ---- PERFORMANCE -------------------------------------------------------
    public boolean isPerformanceMode() { return prefs.getBoolean("performance_mode", false); }
    public void setPerformanceMode(boolean v) { prefs.edit().putBoolean("performance_mode", v).apply(); }

    // ---- TASK MONITOR ------------------------------------------------------
    public boolean isTaskMonitorEnabled() { return prefs.getBoolean("task_monitor", true); }
    public void setTaskMonitorEnabled(boolean v) { prefs.edit().putBoolean("task_monitor", v).apply(); }

    // ---- MEMORY ------------------------------------------------------------
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /** Full JARVIS reset — clears all persisted settings (incl. name/voice/lang). */
    public void resetJarvisPrefs(android.content.Context ctx) {
        clearAll();
        ctx.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE).edit().clear().apply();
    }
}