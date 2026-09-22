package com.example.eisenhowertodo;

final class Task {
    final long id;
    String text;
    int quadrant;
    boolean done;
    final long createdAt;
    long dueAt;
    double order;
    long serverVersion;

    Task(long id, String text, int quadrant, boolean done, long createdAt, long dueAt) {
        this(id, text, quadrant, done, createdAt, dueAt, 0d, 0L);
    }

    Task(long id, String text, int quadrant, boolean done, long createdAt, long dueAt,
         double order, long serverVersion) {
        this.id = id;
        this.text = text;
        this.quadrant = quadrant;
        this.done = done;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
        this.order = order;
        this.serverVersion = serverVersion;
    }
}
