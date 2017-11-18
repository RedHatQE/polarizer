package com.github.redhatqe.polarizer.configuration.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TestCaseImportInfo {
    @JsonProperty
    private String name;
    @JsonProperty
    private String status;
    @JsonProperty
    private String id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
