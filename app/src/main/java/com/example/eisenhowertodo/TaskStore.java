package com.example.eisenhowertodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TaskStore {
    private static final String PREFS = "eisenhower_tasks";
    private static final String KEY_TASKS = "tasks";

    private final SharedPreferences preferences;

    TaskStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<Task> all() {
        ArrayList<Task> tasks = new ArrayList<>();
        String raw = preferences.getString(KEY_TASKS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                tasks.add(new Task(
                        item.getLong("id"),
                        item.getString("text"),
                        item.optInt("quadrant", 0),
                        item.optBoolean("done", false),
                        item.optLong("createdAt", item.getLong("id"))
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_TASKS).apply();
        }
        tasks.sort(Comparator.comparingLong(task -> task.createdAt));
        return tasks;
    }

    synchronized void add(String text, int quadrant) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return;
        List<Task> tasks = all();
        long now = System.currentTimeMillis();
        tasks.add(new Task(now, normalized, clampQuadrant(quadrant), false, now));
        save(tasks);
    }

    synchronized void toggle(long id) {
        List<Task> tasks = all();
        for (Task task : tasks) {
            if (task.id == id) {
                task.done = !task.done;
                break;
            }
        }
        save(tasks);
    }

    synchronized void move(long id, int quadrant) {
        List<Task> tasks = all();
        for (Task task : tasks) {
            if (task.id == id) {
                task.quadrant = clampQuadrant(quadrant);
                break;
            }
        }
        save(tasks);
    }

    synchronized void delete(long id) {
        List<Task> tasks = all();
        tasks.removeIf(task -> task.id == id);
        save(tasks);
    }

    synchronized void clearCompleted() {
        List<Task> tasks = all();
        tasks.removeIf(task -> task.done);
        save(tasks);
    }

    private int clampQuadrant(int quadrant) {
        return Math.max(0, Math.min(3, quadrant));
    }

    private void save(List<Task> tasks) {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", task.id);
                item.put("text", task.text);
                item.put("quadrant", task.quadrant);
                item.put("done", task.done);
                item.put("createdAt", task.createdAt);
                array.put(item);
            } catch (JSONException ignored) {
                // Values used here are JSON-safe primitives.
            }
        }
        preferences.edit().putString(KEY_TASKS, array.toString()).apply();
    }
}
