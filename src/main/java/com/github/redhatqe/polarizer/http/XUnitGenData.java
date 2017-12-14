package com.github.redhatqe.polarizer.http;

import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class XUnitGenData {
    private Set<String> completed;
    private String mapping;
    private String xunitPath;
    private XUnitConfig config;
    private UUID id;
    private static final String[] _done = {"xunit", "xargs", "mapping"};
    private static final Set<String> done = new HashSet<>(Arrays.asList(_done));

    public XUnitGenData() {
        this.completed = new HashSet<>();
    }

    public XUnitGenData(XUnitGenData other) {
        this.completed = new HashSet<>(other.completed);
        this.mapping = other.mapping;
        this.xunitPath = other.xunitPath;
        this.config = new XUnitConfig(other.config);
        this.id = other.id;
    }

    public XUnitGenData(UUID id) {
        this.id = id;
    }

    public XUnitGenData(UUID id, String mapping) {
        this(id);
        this.mapping = mapping;
    }

    public XUnitGenData(String xunitPath, UUID id) {
        this(id);
        this.xunitPath = xunitPath;
    }

    public XUnitGenData(UUID id, XUnitConfig cfg) {
        this(id);
        this.config = new XUnitConfig(cfg);
    }

    public boolean done() {
        return this.completed.containsAll(XUnitGenData.done);
    }

    public Set<String> getCompleted() {
        return completed;
    }

    public void setCompleted(Set<String> completed) {
        this.completed = completed;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
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
}
