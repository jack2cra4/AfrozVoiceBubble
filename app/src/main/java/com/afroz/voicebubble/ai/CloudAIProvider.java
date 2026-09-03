package com.afroz.voicebubble.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Generic OpenAI-compatible chat-completions client used for cloud providers
 * (OpenAI, OpenRouter, Groq, custom OpenAI-compatible endpoints). Also handles
 * the Gemini and Anthropic message shapes via small adapters.
 *
 * Runs synchronously — callers MUST invoke {@link #complete} off the UI thread
 * (the AIProviderManager wraps it in a background worker). On any failure it
 * returns null so JARVIS can fall back to the local engine.
 */
public class CloudAIProvider implements AIProvider {

    private final ProviderConfig config;
    private final APIKeyManager keys;

    public CloudAIProvider(ProviderConfig config, APIKeyManager keys) {
        this.config = config;
        this.keys = keys;
    }

    @Override
    public String name() { return config.name; }

    @Override
    public String model() { return config.model == null ? "" : config.model; }

    @Override
    public boolean isConfigured() {
        if ("local".equalsIgnoreCase(config.name)) return true;
        if (!keys.has(config.name)) return false;
        String k = keys.get(config.name);
        return k != null && !k.isEmpty();
    }

    @Override
    public String complete(String prompt, String systemPrompt) {
        String family = (config.name == null ? "" : config.name.toLowerCase());
        try {
            if (family.equals("gemini")) {
                return gemini(prompt, systemPrompt);
            } else if (family.equals("anthropic")) {
                return anthropic(prompt, systemPrompt);
            }
            return openAiCompatible(prompt, systemPrompt);
        } catch (Exception e) {
            return null;
        }
    }

    private String openAiCompatible(String prompt, String systemPrompt) throws Exception {
        String url = config.resolvedBaseUrl();
        String key = keys.get(config.name);
        JSONObject body = new JSONObject();
        body.put("model", config.model == null || config.model.isEmpty()
                ? ProviderConfig.defaultModel(config.name) : config.model);
        JSONArray msgs = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        }
        msgs.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", msgs);
        body.put("max_tokens", 300);

        HttpURLConnection conn = open(url, "application/json", key);
        try {
            write(conn, body.toString());
            return readJsonChoice(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String gemini(String prompt, String systemPrompt) throws Exception {
        String key = keys.get(config.name);
        if (key == null || key.isEmpty()) return null;
        String url = config.resolvedBaseUrl() + ":generateContent?key=" + key;
        JSONObject system = new JSONObject();
        system.put("parts", new JSONArray().put(new JSONObject()
                .put("text", systemPrompt == null ? "You are JARVIS, a concise assistant." : systemPrompt)));
        JSONObject user = new JSONObject();
        user.put("parts", new JSONArray().put(new JSONObject().put("text", prompt)));
        JSONArray contents = new JSONArray();
        contents.put(new JSONObject().put("role", "user").put("parts", user.getJSONArray("parts")));

        JSONObject body = new JSONObject();
        body.put("systemInstruction", system);
        body.put("contents", contents);

        HttpURLConnection conn = open(url, "application/json", key);
        try {
            write(conn, body.toString());
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            JSONObject j = new JSONObject(sb.toString());
            return j.optJSONArray("candidates").optJSONObject(0)
                    .optJSONObject("content").optJSONArray("parts")
                    .optJSONObject(0).optString("text");
        } finally {
            conn.disconnect();
        }
    }

    private String anthropic(String prompt, String systemPrompt) throws Exception {
        String key = keys.get(config.name);
        if (key == null || key.isEmpty()) return null;
        JSONObject body = new JSONObject();
        body.put("model", config.model == null || config.model.isEmpty()
                ? ProviderConfig.defaultModel(config.name) : config.model);
        body.put("max_tokens", 300);
        body.put("system", systemPrompt == null ? "You are JARVIS, a concise assistant." : systemPrompt);
        body.put("messages", new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", prompt)));

        HttpURLConnection conn = open(config.resolvedBaseUrl(), "application/json", key);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        try {
            write(conn, body.toString());
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            JSONObject j = new JSONObject(sb.toString());
            return j.optJSONArray("content").optJSONObject(0).optString("text");
        } finally {
            conn.disconnect();
        }
    }

    private String readJsonChoice(HttpURLConnection conn) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        JSONObject j = new JSONObject(sb.toString());
        JSONArray choices = j.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return null;
        return choices.optJSONObject(0).optJSONObject("message").optString("content");
    }

    private HttpURLConnection open(String u, String contentType, String key) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setDoOutput(true);
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        return conn;
    }

    private void write(HttpURLConnection conn, String body) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
