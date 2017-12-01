package com.github.redhatqe.polarizer.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class APITestSuiteConfig {
    @JsonProperty
    public String host;
    @JsonProperty
    public int port;
    @JsonProperty(required = true)
    public String jarPath;


}
