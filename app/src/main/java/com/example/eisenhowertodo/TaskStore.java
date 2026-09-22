package com.example.eisenhowertodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

final class TaskStore {
    private static final String PREFS = "eisenhower_tasks";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_PENDING = "sync_pending";
    private static final String KEY_SYNC_INITIALIZED = "sync_initialized";

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
                        item.optLong("dueAt", 0L),
                        item.optDouble("order", index * 1024d),
                        item.optLong("version", 0L)
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
        long id = now * 1000L + ThreadLocalRandom.current().nextInt(1000);
        Task added = new Task(id, normalized, clampQuadrant(quadrant), false, now,
                Math.max(0L, dueAt));
        added.order = firstOrderOfQuadrant(tasks, added.quadrant) - 1024d;
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
                task.order = firstOrderOfQuadrant(tasks, target) - 1024d;
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
                task.order = firstOrderOfQuadrant(tasks, target) - 1024d;
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
        normalizeOrder(tasks, dragged.quadrant);
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
        normalizeOrder(tasks, quadrant);
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

    private double firstOrderOfQuadrant(List<Task> tasks, int quadrant) {
        double first = 0d;
        for (Task task : tasks) {
            if (task.quadrant == quadrant) first = Math.min(first, task.order);
        }
        return first;
    }

    private void normalizeOrder(List<Task> tasks, int quadrant) {
        int index = 0;
        for (Task task : tasks) {
            if (task.quadrant == quadrant) task.order = index++ * 1024d;
        }
    }

    synchronized void initializeSync() {
        if (preferences.getBoolean(KEY_SYNC_INITIALIZED, false)) return;
        JSONArray pending = pending();
        for (Task task : all()) pending = queue(pending, "upsert", task);
        preferences.edit().putString(KEY_PENDING, pending.toString())
                .putBoolean(KEY_SYNC_INITIALIZED, true).apply();
    }

    synchronized JSONArray pending() {
        try { return new JSONArray(preferences.getString(KEY_PENDING, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    synchronized void applyRemote(JSONObject response, JSONArray sent) throws JSONException {
        JSONArray incoming = response.getJSONArray("tasks");
        Set<String> sentIds = new HashSet<>();
        for (int index = 0; index < sent.length(); index++) {
            sentIds.add(sent.getJSONObject(index).getString("opId"));
        }
        JSONArray outstanding = new JSONArray();
        Set<Long> localIds = new HashSet<>();
        JSONArray currentPending = pending();
        for (int index = 0; index < currentPending.length(); index++) {
            JSONObject operation = currentPending.getJSONObject(index);
            if (sentIds.contains(operation.getString("opId"))) continue;
            long id = operation.getLong("id");
            localIds.add(id);
            outstanding.put(operation);
        }
        Map<Long, Task> local = new HashMap<>();
        for (Task task : all()) local.put(task.id, task);
        List<Task> merged = new ArrayList<>();
        for (int index = 0; index < incoming.length(); index++) {
            JSONObject item = incoming.getJSONObject(index);
            long id = item.getLong("id");
            long version = item.optLong("version", 0L);
            for (int opIndex = 0; opIndex < outstanding.length(); opIndex++) {
                JSONObject operation = outstanding.getJSONObject(opIndex);
                if (operation.getLong("id") == id) operation.put("baseVersion", version);
            }
            if (localIds.contains(id)) {
                Task pendingTask = local.get(id);
                if (pendingTask != null) merged.add(pendingTask);
                continue;
            }
            if (!item.optBoolean("deleted", false)) merged.add(readTask(item, index));
        }
        for (Long id : localIds) {
            boolean exists = false;
            for (Task task : merged) if (task.id == id) { exists = true; break; }
            if (!exists && local.containsKey(id)) merged.add(local.get(id));
        }
        merged.sort((a, b) -> {
            int quadrantOrder = Integer.compare(a.quadrant, b.quadrant);
            if (quadrantOrder != 0) return quadrantOrder;
            int rankOrder = Double.compare(a.order, b.order);
            return rankOrder != 0 ? rankOrder : Long.compare(a.createdAt, b.createdAt);
        });
        preferences.edit().putString(KEY_TASKS, taskArray(merged).toString())
                .putString(KEY_PENDING, outstanding.toString()).apply();
    }

    private Task readTask(JSONObject item, int index) throws JSONException {
        return new Task(item.getLong("id"), item.getString("text"),
                item.optInt("quadrant", 0), item.optBoolean("done", false),
                item.optLong("createdAt", item.getLong("id")), item.optLong("dueAt", 0L),
                item.optDouble("order", index * 1024d), item.optLong("version", 0L));
    }

    private JSONArray taskArray(List<Task> tasks) {
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
                item.put("order", task.order);
                item.put("version", task.serverVersion);
                array.put(item);
            } catch (JSONException ignored) { }
        }
        return array;
    }

    private JSONArray queue(JSONArray pending, String kind, Task task) {
        String id = String.valueOf(task.id);
        long baseVersion = task.serverVersion;
        JSONArray remaining = new JSONArray();
        for (int index = 0; index < pending.length(); index++) {
            JSONObject previous = pending.optJSONObject(index);
            if (previous == null) continue;
            if (id.equals(previous.optString("id"))) baseVersion = previous.optLong("baseVersion", 0L);
            else remaining.put(previous);
        }
        JSONObject operation = new JSONObject();
        try {
            operation.put("opId", "android." + java.util.UUID.randomUUID());
            operation.put("kind", kind);
            operation.put("id", id);
            operation.put("baseVersion", baseVersion);
            if ("upsert".equals(kind)) operation.put("task", taskArray(java.util.Collections.singletonList(task)).getJSONObject(0));
            remaining.put(operation);
        } catch (JSONException ignored) { }
        return remaining;
    }

    private void save(List<Task> tasks) {
        Map<Long, Task> previous = new HashMap<>();
        for (Task task : all()) previous.put(task.id, task);
        JSONArray operations = pending();
        for (Task task : tasks) {
            Task old = previous.remove(task.id);
            if (old == null || !old.text.equals(task.text) || old.quadrant != task.quadrant ||
                    old.done != task.done || old.dueAt != task.dueAt || old.order != task.order) {
                operations = queue(operations, "upsert", task);
            }
        }
        for (Task deleted : previous.values()) {
            operations = queue(operations, "delete", deleted);
        }
        preferences.edit().putString(KEY_TASKS, taskArray(tasks).toString())
                .putString(KEY_PENDING, operations.toString()).apply();
    }
}
