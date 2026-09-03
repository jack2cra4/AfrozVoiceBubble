package com.afroz.voicebubble.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local session memory manager.
 *
 * Stores useful assistant context (last user name, active task summary, last
 * conversation summary, enabled-agent count, configured providers) in a view
 * that the MEMORY screen can show, and provides clear/reset actions. It never
 * stores microphone recordings or screenshots permanently. Cloud keys are kept
 * separately via the secure APIKeyManager and are never shown here.
 */
public class MemoryManager {

    private final Context context;
    private final SharedPreferences prefs;

    public MemoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE);
    }

    public void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public String get(String key) {
        return prefs.getString(key, "");
    }

    /** Dump current memory as readable lines for the MEMORY screen. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        String name = prefs.getString("user_name", "अफ़रोज़");
        sb.append("• User: ").append(name).append("\n");
        sb.append("• Language: ").append(prefs.getString("lang_mode", "auto")).append("\n");
        sb.append("• Voice: ").append(prefs.getBoolean("voice_male", true) ? "JARVIS Male" : "Female").append("\n");
        sb.append("• Active tasks: ").append(prefs.getInt("active_tasks", 0)).append("\n");
        sb.append("• Agents enabled: ").append(prefs.getInt("agents_enabled", 0)).append("\n");
        sb.append("• Providers configured: ").append(prefs.getInt("providers", 0)).append("\n");
        String last = prefs.getString("last_conversation", "");
        if (!last.isEmpty()) sb.append("• Last topic: ").append(last).append("\n");
        return sb.toString();
    }

    public void clearConversation() {
        prefs.edit().remove("last_conversation")
                .remove("screen_history")
                .apply();
    }

    public void clearMemory() {
        prefs.edit().clear().apply();
    }
}
