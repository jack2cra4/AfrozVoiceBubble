package com.afroz.voicebubble.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted configuration for a single API provider (provider name, base URL,
 * model, API key reference). Keys themselves are held by {@link APIKeyManager}
 * in secure storage — never in this object.
 */
public class ProviderConfig {

    public final String id;
    public String name;        // display name, e.g. "OpenAI"
    public String baseUrl;     // optional override
    public String model;       // optional model id
    public String status;      // "NOT_TESTED" / "CONNECTED" / "INVALID"

    public ProviderConfig(String id, String name, String baseUrl, String model) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.model = model;
        this.status = "NOT_TESTED";
    }

    private static final String PREFS = "jarvis_providers";

    /** The system base URL for a provider family. */
    public String resolvedBaseUrl() {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) return baseUrl.trim();
        return defaultBaseUrl(name);
    }

    public static String defaultBaseUrl(String family) {
        switch (family == null ? "" : family.toLowerCase()) {
            case "openai": return "https://api.openai.com/v1/chat/completions";
            case "gemini": return "https://generativelanguage.googleapis.com/v1beta/models";
            case "anthropic": return "https://api.anthropic.com/v1/messages";
            case "openrouter": return "https://openrouter.ai/api/v1/chat/completions";
            case "groq": return "https://api.groq.com/openai/v1/chat/completions";
            case "local": return "http://127.0.0.1:11434/v1/chat/completions";
            default: return "";
        }
    }

    public static String defaultModel(String family) {
        switch (family == null ? "" : family.toLowerCase()) {
            case "openai": return "gpt-4o-mini";
            case "gemini": return "gemini-1.5-flash";
            case "anthropic": return "claude-3-5-haiku-latest";
            case "openrouter": return "openai/gpt-4o-mini";
            case "groq": return "llama-3.1-8b-instant";
            case "local": return "llama3";
            default: return "";
        }
    }
}
