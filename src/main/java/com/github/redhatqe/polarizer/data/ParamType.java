package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.Param;

public class ParamType {
    @JsonProperty
    private String name;
    @JsonProperty
    private String scope;

    public ParamType() {

    }

    public ParamType(Param p) {
        this.scope = p.scope();
        this.name = p.name();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
