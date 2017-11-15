package com.github.redhatqe.polarizer.configuration.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.configuration.ServerInfo;


import java.util.Map;

public abstract class BaseConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty(required=true)
    protected String project;
    @JsonProperty(required=true)
    protected Map<String, ServerInfo> servers;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================


    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public Map<String, ServerInfo> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerInfo> servers) {
        this.servers = servers;
    }
}
