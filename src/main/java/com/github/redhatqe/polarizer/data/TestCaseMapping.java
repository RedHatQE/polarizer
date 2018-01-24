package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.reporter.IdParams;

import java.util.Map;

public class TestCaseMapping {
    @JsonProperty
    private Map<String, Map<String, IdParams>> mapping;
}
