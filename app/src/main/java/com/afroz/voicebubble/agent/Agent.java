package com.afroz.voicebubble.agent;

import org.json.JSONObject;

/**
 * A configurable AI agent. Stores name, role, description, provider/model,
 * system instructions and enabled state. Used both for the built-in agents and
 * user-created custom agents.
 */
public class Agent {

    public final String id;
    public String name;
    public String role;
    public String description;
    public String provider;      // e.g. "LOCAL", "OpenAI", ...
    public String model;         // optional model id
    public String instructions;  // system instructions
    public boolean enabled;

    public Agent(String id, String name, String role, String description,
                 String provider, String model, String instructions, boolean enabled) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.description = description;
        this.provider = provider;
        this.model = model;
        this.instructions = instructions;
        this.enabled = enabled;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("role", role == null ? "" : role);
            o.put("description", description == null ? "" : description);
            o.put("provider", provider == null ? "" : provider);
            o.put("model", model == null ? "" : model);
            o.put("instructions", instructions == null ? "" : instructions);
            o.put("enabled", enabled);
        } catch (Exception ignored) {}
        return o;
    }

    public static Agent fromJson(JSONObject o) {
        return new Agent(
                o.optString("id"),
                o.optString("name", "Agent"),
                o.optString("role", ""),
                o.optString("description", ""),
                o.optString("provider", ""),
                o.optString("model", ""),
                o.optString("instructions", ""),
                o.optBoolean("enabled", true));
    }
}
