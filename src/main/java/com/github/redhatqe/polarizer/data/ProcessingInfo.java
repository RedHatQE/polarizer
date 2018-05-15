package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.processor.Meta;

import java.util.ArrayList;
import java.util.List;

public class ProcessingInfo {
    @JsonProperty
    private String message;
    @JsonProperty
    private MetaType meta;
    @JsonProperty(value="no-id-funcs")
    private List<String> noIdFuncs;

    public ProcessingInfo() {

    }

    public ProcessingInfo(String msg, Meta<TestDefinition> meta) {
        this.message = msg;
        this.meta = Meta.makeMeta(meta);
        this.noIdFuncs = new ArrayList<>();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MetaType getMeta() {
        return meta;
    }

    public void setMeta(MetaType meta) {
        this.meta = meta;
    }

    public List<String> getNoIdFuncs() {
        return noIdFuncs;
    }

    public void setNoIdFuncs(List<String> noIdFuncs) {
        this.noIdFuncs = noIdFuncs;
    }
}
