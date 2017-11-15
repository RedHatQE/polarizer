package com.github.redhatqe.polarizer.reporter;

import java.util.Arrays;
import java.util.List;

/**
 * Created by stoner on 10/5/16.
 */
public class IdParams {
    public String id = "";
    public List<String> parameters = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public IdParams(String id, List<String> p) {
        this.id = id;
        this.setParameters(p);
    }

    public IdParams(String id, String[] p) {
        this.id = id;
        this.parameters = Arrays.asList(p);
    }

    public IdParams() {

    }
}