package com.github.redhatqe.polarizer.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class TestCaseArgs implements Validator {
    @JsonProperty(required = true)
    private ImportArgs importer;
    @JsonProperty(required = true)
    private FocusedArgs mapper;

    public TestCaseArgs() {

    }

    public Boolean validate() {
        List<Boolean> valid = new ArrayList<>();
        valid.add(this.importer.validate());
        valid.add(this.mapper.validate());
        return valid.stream().allMatch(b -> b);
    }

    public ImportArgs getImporter() {
        return importer;
    }

    public void setImporter(ImportArgs importer) {
        this.importer = importer;
    }

    public FocusedArgs getMapper() {
        return mapper;
    }

    public void setMapper(FocusedArgs mapper) {
        this.mapper = mapper;
    }
}
