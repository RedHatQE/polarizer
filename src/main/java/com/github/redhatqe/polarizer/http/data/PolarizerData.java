package com.github.redhatqe.polarizer.http.data;

import com.github.redhatqe.polarizer.http.Polarizer;

import java.util.Arrays;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class PolarizerData implements IComplete {
    private static final String[] _done = {};
    public static final Set<String> done = new HashSet<>(Arrays.asList(_done));

    protected Set<String> completed;
    protected UUID id;

    public PolarizerData() {
        this.completed = new HashSet<>();
    }

    public PolarizerData(UUID id) {
        this();
        this.id = id;
    }

    public abstract boolean done();

    public abstract int size();

    public Set<String> getCompleted() {
        return completed;
    }

    public void setCompleted(Set<String> completed) {
        this.completed = completed;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public Set<String> completed() {
        return this.completed;
    }

    @Override
    public void addToComplete(String s) {
        this.completed.add(s);
    }
}
