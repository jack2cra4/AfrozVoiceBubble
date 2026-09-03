package com.afroz.voicebubble.agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent Center.
 *
 * Manages the built-in specialised agents (CODER, DEBUGGER, CODE REVIEWER,
 * SECURITY ANALYST, TESTER, ANDROID EXPERT, TERMUX EXPERT, RESEARCHER,
 * TRANSLATOR, SCREEN ANALYST) plus any user-created custom agents. Agents carry
 * role/instructions so JARVIS can route a task to the appropriate specialist.
 */
public class AgentManager {

    private static final String PREFS = "jarvis_agents";
    private static final String KEY = "agents";

    private final SharedPreferences prefs;
    private List<Agent> agents;

    public AgentManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.agents = load();
        if (this.agents.isEmpty()) {
            seedBuiltIn();
        }
    }

    public synchronized List<Agent> all() {
        return new ArrayList<>(agents);
    }

    public synchronized List<Agent> enabled() {
        List<Agent> out = new ArrayList<>();
        for (Agent a : agents) if (a.enabled) out.add(a);
        return out;
    }

    public synchronized Agent find(String id) {
        for (Agent a : agents) if (a.id.equals(id)) return a;
        return null;
    }

    public synchronized Agent add(Agent a) {
        agents.add(a);
        save();
        return a;
    }

    public synchronized boolean remove(String id) {
        Agent a = find(id);
        if (a == null) return false;
        agents.remove(a);
        save();
        return true;
    }

    public synchronized boolean update(Agent updated) {
        Agent a = find(updated.id);
        if (a == null) return false;
        a.name = updated.name;
        a.role = updated.role;
        a.description = updated.description;
        a.provider = updated.provider;
        a.model = updated.model;
        a.instructions = updated.instructions;
        a.enabled = updated.enabled;
        save();
        return true;
    }

    public synchronized void resetDefaults() {
        agents.clear();
        seedBuiltIn();
    }

    public synchronized int enabledCount() {
        int n = 0;
        for (Agent a : agents) if (a.enabled) n++;
        return n;
    }

    private void seedBuiltIn() {
        add(new Agent("coder", "CODER", "Code Writer",
                "Writes and suggests clean code.", "LOCAL", "", "Write concise, correct code.", true));
        add(new Agent("debugger", "DEBUGGER", "Error Debugger",
                "Finds the cause of errors and suggests fixes.", "LOCAL", "",
                "Analyze errors and suggest the likely cause and fix.", true));
        add(new Agent("code_reviewer", "CODE REVIEWER", "Code Reviewer",
                "Reviews code for bugs and improvements.", "LOCAL", "",
                "Review the code and report issues concisely.", false));
        add(new Agent("security", "SECURITY ANALYST", "Security Analyst",
                "Checks for security issues in commands and code.", "LOCAL", "",
                "Flag security concerns in the input.", false));
        add(new Agent("tester", "TESTER", "Tester",
                "Suggests test cases and verifies expectations.", "LOCAL", "",
                "Suggest relevant tests and edge cases.", false));
        add(new Agent("android_expert", "ANDROID EXPERT", "Android Expert",
                "Helps with Android development.", "LOCAL", "",
                "Answer Android development questions.", true));
        add(new Agent("termux_expert", "TERMUX EXPERT", "Termux Expert",
                "Explains terminal errors and fixes in concise Hindi.", "LOCAL", "",
                "Analyze terminal errors and explain fixes in concise Hindi.", true));
        add(new Agent("researcher", "RESEARCHER", "Researcher",
                "Finds and summarises relevant information.", "LOCAL", "",
                "Answer factually and note uncertainty.", false));
        add(new Agent("translator", "TRANSLATOR", "Translator",
                "Translates between Hindi and English.", "LOCAL", "",
                "Translate naturally, keeping meaning.", true));
        add(new Agent("screen_analyst", "SCREEN ANALYST", "Screen Analyst",
                "Analyses the visible screen and explains what is happening.", "LOCAL", "",
                "Summarise what the visible screen shows.", true));
        save();
    }

    // ---- persistence -------------------------------------------------------
    private List<Agent> load() {
        List<Agent> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY, null);
            if (raw == null) return list;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Agent.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Agent a : agents) arr.put(a.toJson());
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
