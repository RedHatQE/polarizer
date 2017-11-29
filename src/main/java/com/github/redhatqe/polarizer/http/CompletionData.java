package com.github.redhatqe.polarizer.http;

import java.util.UUID;

public class CompletionData<T> {
    private String type;
    private T result;
    private final UUID id;

    CompletionData(UUID id) {
        this("", null, id);
    }

    CompletionData(String t, T r, UUID id) {
        this.type = t;
        this.result = r;
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public UUID getId() {
        return id;
    }
}