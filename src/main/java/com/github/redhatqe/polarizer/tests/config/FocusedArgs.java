package com.github.redhatqe.polarizer.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FocusedArgs implements Validator {
    @JsonProperty(required = true)
    private String focus;
    @JsonProperty(required = true)
    private String mapping;
    @JsonProperty(required = true)
    private String args;

    public FocusedArgs() {

    }

    public Boolean validate() {
        List<File> files = new ArrayList<>();
        files.add(new File(this.focus));
        files.add(new File(this.mapping));
        files.add(new File(this.args));
        return files.stream().allMatch(File::exists);
    }

    public String getFocus() {
        return focus;
    }

    public void setFocus(String focus) {
        this.focus = focus;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String xml) {
        this.mapping = xml;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }
}
