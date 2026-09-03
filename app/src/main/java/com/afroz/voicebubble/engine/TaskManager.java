package com.afroz.voicebubble.engine;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local task monitoring and reminders.
 *
 * The user can tell JARVIS to "remember" or "monitor" a task (an Android
 * build, an installation, etc.). Tasks are persisted locally with a status
 * that is updated only from observable information (screen analysis); JARVIS
 * never invents status. Later, JARVIS can remind the user when a task
 * completes or fails based on the live screen analysis.
 */
public class TaskManager {

    public enum Status { RUNNING, COMPLETED, FAILED, PAUSED }

    public static class Task {
        public final String id;
        public String name;
        public Status status;
        public final long createdAt;
        public long startedAt;      // epoch ms (RUNNING baseline)
        public String progress;     // textual progress, nullable
        public String note;         // optional user note / type

        Task(String id, String name, Status status, long createdAt,
             long startedAt, String progress, String note) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.createdAt = createdAt;
            this.startedAt = startedAt;
            this.progress = progress;
            this.note = note;
        }

        public long elapsedMs() {
            long base = status == Status.PAUSED ? startedAt : System.currentTimeMillis();
            return Math.max(0, base - createdAt);
        }
    }

    private static final String PREFS = "jarvis_tasks";
    private static final String KEY_TASKS = "tasks";

    private final SharedPreferences prefs;
    private List<Task> cache;

    public TaskManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.cache = load();
    }

    public synchronized List<Task> all() {
        return new ArrayList<>(cache);
    }

    public synchronized Task find(String id) {
        for (Task t : cache) if (t.id.equals(id)) return t;
        return null;
    }

    public synchronized Task create(String name, Status status, String note) {
        Task t = new Task(String.valueOf(System.currentTimeMillis()),
                name == null ? "Task" : name, status,
                System.currentTimeMillis(), System.currentTimeMillis(), null, note);
        cache.add(0, t);
        save();
        return t;
    }

    public synchronized boolean rename(String id, String name) {
        Task t = find(id);
        if (t == null) return false;
        t.name = name == null ? t.name : name;
        save();
        return true;
    }

    public synchronized boolean setStatus(String id, Status status) {
        Task t = find(id);
        if (t == null) return false;
        if (t.status == Status.RUNNING && status == Status.PAUSED) {
            t.startedAt = System.currentTimeMillis(); // capture pause baseline keep elapsed
        }
        t.status = status;
        save();
        return true;
    }

    public synchronized boolean setProgress(String id, String progress) {
        Task t = find(id);
        if (t == null) return false;
        t.progress = progress;
        save();
        return true;
    }

    public synchronized boolean delete(String id) {
        Task t = find(id);
        if (t == null) return false;
        cache.remove(t);
        save();
        return true;
    }

    public synchronized void clearAll() {
        cache.clear();
        save();
    }

    // ---- persistence -------------------------------------------------------
    private List<Task> load() {
        List<Task> list = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY_TASKS, null);
            if (raw == null) return list;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Task t = new Task(
                        o.optString("id"),
                        o.optString("name", "Task"),
                        parseStatus(o.optString("status", "RUNNING")),
                        o.optLong("created", System.currentTimeMillis()),
                        o.optLong("started", System.currentTimeMillis()),
                        o.optString("progress", null),
                        o.optString("note", null));
                if (t.note != null && t.note.isEmpty()) t.note = null;
                list.add(t);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Task t : cache) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("status", t.status.name());
                o.put("created", t.createdAt);
                o.put("started", t.startedAt);
                o.put("note", t.note == null ? "" : t.note);
                arr.put(o);
            }
            prefs.edit().putString(KEY_TASKS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static Status parseStatus(String s) {
        try { return Status.valueOf(s); } catch (Exception e) { return Status.RUNNING; }
    }
}
