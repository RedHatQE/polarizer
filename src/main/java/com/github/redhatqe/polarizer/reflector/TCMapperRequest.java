package com.github.redhatqe.polarizer.reflector;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.reporter.configuration.ServerInfo;

import java.util.List;
import java.util.Map;

/**
 * This class represents the YAML configuration from polarize-tcmapper.yaml
 */
public class TCMapperRequest {
    @JsonProperty(required=true)
    private String project;
    @JsonProperty(required=true)
    private String author;
    @JsonProperty
    private Map<String, ServerInfo> servers;
    @JsonProperty
    private List<String> packages;
    @JsonIgnore
    private String pathToJar;
    @JsonIgnore
    private String mapping;


    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Map<String, ServerInfo> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerInfo> servers) {
        this.servers = servers;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public String getPathToJar() {
        return pathToJar;
    }

    public void setPathToJar(String pathToJar) {
        this.pathToJar = pathToJar;
    }
}
