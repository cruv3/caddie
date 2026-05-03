package com.llm_smartphone_v2.agent;

public class TaskRequest {
    private final String task;

    public TaskRequest(String task) {
        this.task = task == null ? "" : task.trim();
    }

    public String task() {
        return task;
    }
}
