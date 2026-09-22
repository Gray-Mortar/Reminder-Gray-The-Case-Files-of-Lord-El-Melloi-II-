package com.example.eisenhowertodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
                        item.optLong("createdAt", item.getLong("id")),
                        item.optLong("dueAt", 0L)
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_TASKS).apply();
        }
        return tasks;
    }

    synchronized void add(String text, int quadrant) {
        add(text, quadrant, 0L);
    }

    synchronized void add(String text, int quadrant, long dueAt) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return;
        List<Task> tasks = all();
        long now = System.currentTimeMillis();
        Task added = new Task(now, normalized, clampQuadrant(quadrant), false, now,
                Math.max(0L, dueAt));
        tasks.add(firstIndexOfQuadrant(tasks, added.quadrant), added);
        save(tasks);
    }

    synchronized Task find(long id) {
        for (Task task : all()) {
            if (task.id == id) return task;
        }
        return null;
    }

    synchronized void update(long id, String text, int quadrant, boolean done, long dueAt) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return;
        List<Task> tasks = all();
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            if (task.id != id) continue;
            int target = clampQuadrant(quadrant);
            boolean quadrantChanged = task.quadrant != target;
            task.text = normalized;
            task.quadrant = target;
            task.done = done;
            task.dueAt = Math.max(0L, dueAt);
            if (quadrantChanged) {
                tasks.remove(index);
                tasks.add(firstIndexOfQuadrant(tasks, target), task);
            }
            break;
        }
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
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            if (task.id != id) continue;
            int target = clampQuadrant(quadrant);
            if (task.quadrant != target) {
                tasks.remove(index);
                task.quadrant = target;
                tasks.add(firstIndexOfQuadrant(tasks, target), task);
            }
            break;
        }
        save(tasks);
    }

    synchronized void reorder(long draggedId, long anchorId, boolean placeAfter) {
        if (draggedId == anchorId) return;
        List<Task> tasks = all();
        Task dragged = null;
        for (Task task : tasks) {
            if (task.id == draggedId) {
                dragged = task;
                break;
            }
        }
        if (dragged == null) return;
        tasks.remove(dragged);
        int anchorIndex = -1;
        for (int index = 0; index < tasks.size(); index++) {
            Task candidate = tasks.get(index);
            if (candidate.id == anchorId && candidate.quadrant == dragged.quadrant) {
                anchorIndex = index;
                break;
            }
        }
        if (anchorIndex < 0) return;
        tasks.add(placeAfter ? anchorIndex + 1 : anchorIndex, dragged);
        save(tasks);
    }

    synchronized void moveToEnd(long id, int quadrant) {
        List<Task> tasks = all();
        Task dragged = null;
        for (Task task : tasks) {
            if (task.id == id && task.quadrant == quadrant) {
                dragged = task;
                break;
            }
        }
        if (dragged == null) return;
        tasks.remove(dragged);
        int insertAt = tasks.size();
        for (int index = tasks.size() - 1; index >= 0; index--) {
            if (tasks.get(index).quadrant == quadrant) {
                insertAt = index + 1;
                break;
            }
        }
        tasks.add(insertAt, dragged);
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

    private int firstIndexOfQuadrant(List<Task> tasks, int quadrant) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).quadrant == quadrant) return index;
        }
        return tasks.size();
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
                item.put("dueAt", task.dueAt);
                array.put(item);
            } catch (JSONException ignored) {
                // Values used here are JSON-safe primitives.
            }
        }
        preferences.edit().putString(KEY_TASKS, array.toString()).apply();
    }
}
