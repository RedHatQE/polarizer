package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TestCaseNeedingImport {
    @JsonProperty(required=true)
    List<String> titles;
    @JsonProperty(required = false)
    String message;

    public List<String> getTitles() {
        return titles;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
