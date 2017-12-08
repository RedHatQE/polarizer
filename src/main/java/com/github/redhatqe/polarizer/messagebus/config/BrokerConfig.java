package com.github.redhatqe.polarizer.messagebus.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


/**
 * A simple configuration class to set required parameters such as broker urls or timeouts.  An example JSON configuration
 * is in src/main/resources/skeleton.json, and a YAML configuration is in src/main/resources/skeleton.yaml
 */
public class BrokerConfig {
    // =========================================================================
    // 1. Add all properties for your class/configuration
    // =========================================================================
    @JsonProperty
    private Map<String, Broker> brokers;
    @JsonProperty
    private String defaultBroker;

    // ==========================================================================
    // 2. Add all fields not belonging to the configuration here
    // ==========================================================================
    @JsonIgnore
    public static final String configBasePath = ".polarizer";
    @JsonIgnore
    public static final String defaultConfigFileName = "broker-config.yml";

    // =========================================================================
    // 3. Constructors go here.  Remember that there must be a no-arg constructor and you usually want a copy con
    // =========================================================================
    public BrokerConfig(String name, String url, String user, String pw, Long to, Integer max) {
        this();
        Broker broker = new Broker(url, user, pw, to, max);
        this.brokers.put(name, broker);
        this.defaultBroker = name;
    }

    public BrokerConfig() {
        this.brokers = new HashMap<>();
        this.defaultBroker = "ci";
    }

    /**
     * Create a new BrokerConfig with the same values as the instance passed in
     */
    public BrokerConfig(BrokerConfig cfg) {
        this();
        cfg.getBrokers().forEach((k, v) -> this.brokers.put(k, new Broker(v)));
        this.defaultBroker = cfg.defaultBroker;
    }

    //=============================================================================
    // 4. Define the bean setters and getters for all fields in #1
    //=============================================================================
    public Map<String, Broker> getBrokers() {
        return this.brokers;
    }

    public void setBrokers(Map<String, Broker> b) {
        this.brokers = b;
    }

    public String getDefaultBroker() {
        return this.defaultBroker;
    }

    public void setDefaultBroker(String def) {
        this.defaultBroker = def;
    }

    //=============================================================================
    // 5. Define any other functions
    //=============================================================================
    public void addBroker(String name, Broker b) {
        this.brokers.put(name, b);
    }

    public static String getDefaultConfigPath() {
        return Paths.get(System.getProperty("user.home"), configBasePath, defaultConfigFileName).toString();
    }

    // FIXME: Replace this with a real test
    public static void main(String[] args) {
        BrokerConfig cfg = new BrokerConfig("ci", "ci-labs.eng.rdu2:61616", "stoner", "foo", 300000L, 1);
        BrokerConfig cfg2 = new BrokerConfig("ci", "ci-labs-foo", "stoner", "bar", 60000L, -1);
        //cfg2.parse(args);

        Broker b = new Broker("ci-labs.eng.rdu2:61613", "foo", "bar", 1000L, 1);
        cfg.addBroker("metrics", b);
        cfg2.addBroker("metrics", b);
        try {
            String testpath = "/tmp/testing.json";
            Serializer.toJson(cfg, testpath);
            Serializer.toYaml(cfg, "/tmp/testing.yaml");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BrokerConfig readIn = Serializer.fromJson(BrokerConfig.class, new File("/tmp/testing.json"));
            Serializer.toYaml(cfg2, "/tmp/testing2.yaml");
            BrokerConfig cfgYaml = Serializer.fromYaml(BrokerConfig.class, new File("/tmp/testing2.yaml"));
            Broker broker = readIn.getBrokers().get("ci");
            System.out.println(readIn.getDefaultBroker());
            System.out.println(cfgYaml.getBrokers().get("ci").getUrl());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
