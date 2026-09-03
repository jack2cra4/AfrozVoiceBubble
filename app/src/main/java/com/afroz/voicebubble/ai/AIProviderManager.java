package com.afroz.voicebubble.ai;

import android.content.Context;
import android.content.SharedPreferences;

import com.afroz.voicebubble.chat.JarvisBrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Provider Manager.
 *
 * Maintains configured provider families (OpenAI, Gemini, Anthropic,
 * OpenRouter, Groq, Local, Custom), creates {@link AIProvider} instances, and
 * provides task-based routing with automated fallback:
 *
 *   Provider A -&gt; failure -&gt; Provider B -&gt; failure -&gt; Local fallback.
 *
 * Cloud calls run on a background worker so the UI thread / TTS is never
 * blocked. The local provider always succeeds offline.
 */
public class AIProviderManager {

    private static final String PREFS = "jarvis_providers";
    private static final String KEY = "providers";

    private final Context context;
    private final SharedPreferences prefs;
    private final APIKeyManager keys;
    private final LocalAIProvider local;   // permanent offline fallback

    private List<ProviderConfig> configs;

    public AIProviderManager(Context context, JarvisBrain brain) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.keys = new APIKeyManager(context);
        this.local = new LocalAIProvider(brain);
        this.configs = load();
        if (this.configs.isEmpty()) {
            seedDefaults();
        }
    }

    public List<ProviderConfig> providers() {
        return new ArrayList<>(configs);
    }

    public ProviderConfig get(String id) {
        for (ProviderConfig c : configs) if (c.id.equals(id)) return c;
        return null;
    }

    public ProviderConfig add(ProviderConfig c) {
        configs.add(c);
        save();
        return c;
    }

    public boolean remove(String id) {
        ProviderConfig c = get(id);
        if (c == null) return false;
        configs.remove(c);
        keys.remove(c.name);
        save();
        return true;
    }

    public boolean update(ProviderConfig c) {
        ProviderConfig old = get(c.id);
        if (old == null) return false;
        old.name = c.name;
        old.baseUrl = c.baseUrl;
        old.model = c.model;
        old.status = c.status;
        save();
        return true;
    }

    /** Provide the task through a family/agent, with fallback to local. */
    public String route(String family, String prompt, String systemPrompt) {
        AIProvider preferred = providerFor(family);
        if (preferred != null && preferred.isConfigured()) {
            String r = runCloud(preferred, prompt, systemPrompt);
            if (r != null && !r.trim().isEmpty()) return r;
        }
        // Local fallback (offline always works).
        return runLocal(prompt, systemPrompt);
    }

    /** Route using the first available provider, else local. */
    public String routeAny(String prompt, String systemPrompt) {
        // Try each configured + ready provider in order, then local.
        for (ProviderConfig c : configs) {
            AIProvider p = providerFor(c.name);
            if (p != null && p.isConfigured() && !"LOCAL".equalsIgnoreCase(p.name())) {
                String r = runCloud(p, prompt, systemPrompt);
                if (r != null && !r.trim().isEmpty()) return r;
            }
        }
        return runLocal(prompt, systemPrompt);
    }

    public AIProvider providerFor(String family) {
        if (family == null) return local;
        String f = family.toLowerCase();
        if (f.equals("local")) return local;
        ProviderConfig c = get(f);
        if (c == null) return null;
        return new CloudAIProvider(c, keys);
    }

    public String runCloud(AIProvider p, String prompt, String systemPrompt) {
        try {
            return p.complete(prompt, systemPrompt);
        } catch (Exception e) {
            return null;
        }
    }

    public String runLocal(String prompt, String systemPrompt) {
        return local.complete(prompt, systemPrompt);
    }

    public APIKeyManager keys() {
        return keys;
    }

    public int configuredCount() {
        int n = 0;
        for (ProviderConfig c : configs) if (isReady(c.name)) n++;
        return n;
    }

    public boolean isReady(String family) {
        if ("local".equalsIgnoreCase(family)) return true;
        return keys.has(family);
    }

    public String statusText(String family) {
        if ("local".equalsIgnoreCase(family)) return "AVAILABLE";
        if (!keys.has(family)) return "NOT TESTED";
        return "CONNECTED";
    }

    // ---- persistence -------------------------------------------------------
    private List<ProviderConfig> load() {
        List<ProviderConfig> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY, null);
            if (raw == null) return list;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ProviderConfig c = new ProviderConfig(
                        o.optString("id"), o.optString("name"),
                        o.optString("baseUrl", null), o.optString("model", null));
                c.status = o.optString("status", "NOT_TESTED");
                list.add(c);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (ProviderConfig c : configs) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("name", c.name);
                o.put("baseUrl", c.baseUrl == null ? "" : c.baseUrl);
                o.put("model", c.model == null ? "" : c.model);
                o.put("status", c.status == null ? "NOT_TESTED" : c.status);
                arr.put(o);
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void seedDefaults() {
        add(new ProviderConfig("openai", "openai", "", ProviderConfig.defaultModel("openai")));
        add(new ProviderConfig("gemini", "gemini", "", ProviderConfig.defaultModel("gemini")));
        add(new ProviderConfig("anthropic", "anthropic", "", ProviderConfig.defaultModel("anthropic")));
        add(new ProviderConfig("openrouter", "openrouter", "", ProviderConfig.defaultModel("openrouter")));
        add(new ProviderConfig("groq", "groq", "", ProviderConfig.defaultModel("groq")));
        add(new ProviderConfig("local", "local", "", ProviderConfig.defaultModel("local")));
    }
}
