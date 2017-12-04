package com.github.redhatqe.polarizer.configuration.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.configuration.ServerInfo;
import com.github.redhatqe.polarizer.configuration.XUnitInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XUnitConfig extends BaseConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty(required=true)
    private XUnitInfo xunit;
    @JsonProperty
    private String mapping;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    public final String properties = "properties";
    @JsonIgnore
    public final String testSuite = "test-suite";
    @JsonIgnore
    public static Logger logger = LogManager.getLogger("XUnitConfig");
    @JsonIgnore
    private String currentXUnit;
    @JsonIgnore
    private String newXunit;
    @JsonIgnore
    public final String polarionServer = "polarion";
    @JsonIgnore
    public final String brokerServer = "broker";
    @JsonIgnore
    private String newConfigPath = "";
    @JsonIgnore
    public List<String> completed;


    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public XUnitConfig() {
        this.servers = new HashMap<>();
        this.completed = new ArrayList<>();
    }

    /**
     * This should be like a copy constructor
     * @param cfg
     */
    public XUnitConfig(XUnitConfig cfg) {
        this();
        Map<String, ServerInfo> servers_ = cfg.getServers();
        servers_.forEach((String name, ServerInfo si) -> this.servers.put(name, new ServerInfo(si)));
        this.mapping = cfg.getMapping();
        this.project = cfg.getProject();
        this.xunit = cfg.getXunit();
    }

    public Object deepCopy() {
        XUnitConfig cfg = new XUnitConfig();
        this.getServers().forEach((name, si) -> cfg.servers.put(name, new ServerInfo(si)));
        cfg.mapping = this.getMapping();
        cfg.project = this.getProject();
        cfg.xunit = this.getXunit();
        cfg.completed = this.completed;
        return cfg;
    }


    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public XUnitInfo getXunit() {
        return xunit;
    }

    public void setXunit(XUnitInfo xunit) {
        this.xunit = xunit;
    }

    public String getCurrentXUnit() {
        return currentXUnit;
    }

    public void setCurrentXUnit(String currentXUnit) {
        this.currentXUnit = currentXUnit;
    }

    public String getNewXunit() {
        return newXunit;
    }

    public void setNewXunit(String newXunit) {
        this.newXunit = newXunit;
    }

    public String getNewConfigPath() {
        return newConfigPath;
    }

    public void setNewConfigPath(String newConfigPath) {
        this.newConfigPath = newConfigPath;
    }

}
