package com.example.eisenhowertodo;

final class Task {
    final long id;
    String text;
    int quadrant;
    boolean done;
    final long createdAt;
    long dueAt;

    Task(long id, String text, int quadrant, boolean done, long createdAt, long dueAt) {
        this.id = id;
        this.text = text;
        this.quadrant = quadrant;
        this.done = done;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
    }
}
