package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.Param;
import com.github.redhatqe.polarize.metadata.TestStep;


import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestStepType implements TestStep {
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

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ParamType> getParams() {
        return params;
    }

    public void setParams(List<ParamType> params) {
        this.params = params;
    }

    @Override
    public String expected() {
        return this.expected;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public Param[] params() {
        return params.toArray(new Param[this.params.size()]);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return TestStepType.class;
    }
}
