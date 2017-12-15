package com.github.redhatqe.polarizer.http.data;

import com.github.redhatqe.polarizer.reporter.configuration.data.XUnitConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class XUnitGenData extends XUnitData {
    private String mapping;
    private static final String[] _done = {"xunit", "xargs", "mapping"};
    public static final Set<String> done = new HashSet<>(Arrays.asList(_done));

    public XUnitGenData() {
        super();
    }

    public XUnitGenData(UUID id) {
        super(id);
    }

    public XUnitGenData(UUID id, String mapping) {
        super(id);
        this.mapping = mapping;
    }

    public XUnitGenData(XUnitGenData other) {
        this.completed = new HashSet<>(other.completed);
        this.xunitPath = other.xunitPath;
        this.config = new XUnitConfig(other.config);
        this.mapping = other.mapping;
        this.id = other.id;
    }

    public XUnitGenData merge(XUnitGenData other) {
        if (other.completed != null)
            this.completed.addAll(other.completed);
        if (other.xunitPath != null)
            this.xunitPath = other.xunitPath;
        if (other.config != null)
            this.config = new XUnitConfig(other.config);
        if (other.mapping != null)
            this.mapping = other.mapping;
        return this;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }
}
