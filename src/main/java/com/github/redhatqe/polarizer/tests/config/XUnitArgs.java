package com.github.redhatqe.polarizer.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class XUnitArgs implements Validator {
    @JsonProperty(required = true)
    private ImportArgs importer;
    @JsonProperty(required = true)
    private FocusedArgs generate;

    public XUnitArgs() {

    }

    public Boolean validate() {
        List<Boolean> valid = new ArrayList<>();
        valid.add(this.importer.validate());
        valid.add(this.generate.validate());
        return valid.stream().allMatch(b -> b);
    }

    public ImportArgs getImporter() {
        return importer;
    }

    public void setImporter(ImportArgs importer) {
        this.importer = importer;
    }

    public FocusedArgs getGenerate() {
        return generate;
    }

    public void setGenerate(FocusedArgs generate) {
        this.generate = generate;
    }
}
