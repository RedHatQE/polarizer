package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.processor.Meta;

public class ProcessingInfo {
    @JsonProperty
    private String message;
    @JsonProperty
    private MetaType meta;

    public ProcessingInfo() {

    }

    public ProcessingInfo(String msg, Meta<TestDefinition> meta) {
        this.message = msg;
        this.meta = Meta.makeMeta(meta);
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
}
