package com.example.eisenhowertodo;

final class Task {
    final long id;
    final String text;
    int quadrant;
    boolean done;
    final long createdAt;

    Task(long id, String text, int quadrant, boolean done, long createdAt) {
        this.id = id;
        this.text = text;
        this.quadrant = quadrant;
        this.done = done;
        this.createdAt = createdAt;
    }
}
