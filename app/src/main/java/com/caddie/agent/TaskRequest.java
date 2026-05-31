package com.caddie.agent;

public class TaskRequest {
    private final String task;

    public TaskRequest(String task) {
        this.task = task == null ? "" : task.trim();
    }

    public String task() {
        return task;
    }
}
