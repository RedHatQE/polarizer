package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.importer.testcase.Parameter;
import com.github.redhatqe.polarizer.processor.Meta;

import java.util.List;

public class MetaType {
    @JsonProperty(value="package-name")
    public String packName;
    @JsonProperty(value="class-name")
    public String className;
    @JsonProperty(value="method-name")
    public String methName;
    @JsonProperty(value="qualified-name")
    public String qualifiedName;
    @JsonProperty
    public String project;
    @JsonProperty
    public TestDefinitionType annotation;
    @JsonProperty
    public List<Parameter> params;
    @JsonProperty(value="polarion-id")
    public String polarionID;
    @JsonProperty
    public Boolean dirty;

    public MetaType() {

    }

    public MetaType(Meta<TestDefinition> meta) {
        this.packName = meta.packName;
        this.className = meta.className;
        this.methName = meta.methName;
        this.qualifiedName = meta.qualifiedName;
        this.project = meta.project;
        this.polarionID = meta.polarionID;
        this.dirty = meta.dirty;
        this.annotation = new TestDefinitionType(meta.annotation);
    }
}
