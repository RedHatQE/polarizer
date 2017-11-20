package com.github.redhatqe.polarizer.messagebus;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultResult {
    @JsonProperty
    private String text;

    public DefaultResult() {

    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
