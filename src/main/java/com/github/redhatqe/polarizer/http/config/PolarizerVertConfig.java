package com.github.redhatqe.polarizer.http.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PolarizerVertConfig {
    @JsonProperty
    private int port;

    // TODO: SSL passwords, keystore and truststore keys
}
