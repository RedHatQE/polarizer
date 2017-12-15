package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.http.data.IComplete;
import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class XUnitData implements IComplete {
    protected Set<String> completed;
    protected String xunitPath;
    protected XUnitConfig config;
    protected UUID id;
    private static final String[] _done = {"xunit", "xargs"};
    public static final Set<String> done = new HashSet<>(Arrays.asList(_done));

    public XUnitData() {
        this.completed = new HashSet<>();
    }

    public XUnitData(XUnitData other) {
        this.completed = new HashSet<>(other.completed);
        this.xunitPath = other.xunitPath;
        this.config = new XUnitConfig(other.config);
        this.id = other.id;
    }

    public XUnitData merge(XUnitData other) {
        if (other.completed != null)
            this.completed.addAll(other.completed);
        if (other.xunitPath != null) {
            this.xunitPath = other.xunitPath;
            if (this.config != null)
                this.config.setCurrentXUnit(this.xunitPath);
        }
        if (other.config != null) {
            this.config = new XUnitConfig(other.config);
            if (this.xunitPath != null)
                this.config.setCurrentXUnit(this.xunitPath);
        }
        return this;
    }

    public XUnitData(UUID id) {
        this();
        this.id = id;
    }

    public XUnitData(UUID id, String xunitPath) {
        this(id);
        this.xunitPath = xunitPath;
    }

    public XUnitData(UUID id, XUnitConfig cfg) {
        this(id);
        this.config = new XUnitConfig(cfg);
    }

    public boolean done() {
        return this.completed.containsAll(XUnitData.done);
    }

    public int size() {
        return XUnitData.done.size();
    }

    public Set<String> getCompleted() {
        return completed;
    }

    public void setCompleted(Set<String> completed) {
        this.completed = completed;
    }

    public String getXunitPath() {
        return xunitPath;
    }

    public void setXunitPath(String xunitPath) {
        this.xunitPath = xunitPath;
    }

    public XUnitConfig getConfig() {
        return config;
    }

    public void setConfig(XUnitConfig config) {
        this.config = config;
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
