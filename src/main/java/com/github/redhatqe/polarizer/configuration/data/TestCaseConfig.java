package com.github.redhatqe.polarizer.configuration.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.configuration.ServerInfo;
import com.github.redhatqe.polarizer.configuration.TestCaseInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCaseConfig extends BaseConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty(required=true)
    private TestCaseInfo testcase;
    @JsonProperty
    private String author;
    @JsonProperty
    private List<String> packages;


    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    private String mapping;
    @JsonIgnore
    public static Logger logger = LogManager.getLogger("XUnitConfig");
    @JsonIgnore
    private String pathToJar;  // Path to the downloaded jar
    @JsonIgnore
    public static final String configBasePath = ".polarizer";
    @JsonIgnore
    public static final String defaultConfigFileName = "polarizer-testcase.yml";
    @JsonIgnore
    public List<String> completed;

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor
    // =========================================================================
    public TestCaseConfig() {
        this.servers = new HashMap<>();
        this.completed = new ArrayList<>(3);
    }

    /**
     * This should be like a copy constructor
     * @param cfg
     */
    public TestCaseConfig(TestCaseConfig cfg) {
        this();
        Map<String, ServerInfo> servers_ = cfg.getServers();
        servers_.forEach((String name, ServerInfo si) -> this.servers.put(name, new ServerInfo(si)));
        this.mapping = cfg.getMapping();
        this.project = cfg.getProject();
        this.testcase = cfg.getTestcase();
    }

    public TestCaseConfig deepCopy() {
        TestCaseConfig cfg = new TestCaseConfig();
        this.getServers().forEach((name, si) -> cfg.servers.put(name, new ServerInfo(si)));
        cfg.mapping = this.getMapping();
        cfg.project = this.getProject();
        cfg.testcase = this.getTestcase();
        return cfg;
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================

    public TestCaseInfo getTestcase() {
        return testcase;
    }

    public void setTestcase(TestCaseInfo testcase) {
        this.testcase = testcase;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getPathToJar() {
        return pathToJar;
    }

    public void setPathToJar(String pathToJar) {
        this.pathToJar = pathToJar;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public static String getDefaultConfigPath() {
        return Paths.get(System.getProperty("user.home"), configBasePath, defaultConfigFileName).toString();
    }
}
