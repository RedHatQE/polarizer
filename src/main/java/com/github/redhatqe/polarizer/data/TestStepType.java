package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.TestStep;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestStepType {
    @JsonProperty
    private String expected;         // Optional: What the expected value should be from running this
    @JsonProperty
    private String description;      // Optional: Description of what the step does
    @JsonProperty
    private List<ParamType> params;

    public TestStepType() {

    }

    public TestStepType(TestStep ts) {
        this.expected = ts.expected();
        this.description = ts.description();
        this.params = Arrays.stream(ts.params())
                .map(ParamType::new)
                .collect(Collectors.toList());
    }
}
