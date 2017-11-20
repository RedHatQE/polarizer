package com.github.redhatqe.polarizer.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarize.metadata.DefTypes;
import com.github.redhatqe.polarize.metadata.TestType;

import java.lang.annotation.Annotation;

public class TestTypeType implements TestType {
    @JsonProperty
    private DefTypes.TestTypes testtype;
    @JsonProperty
    private DefTypes.Subtypes subtype1;
    @JsonProperty
    private DefTypes.Subtypes subtype2;

    public TestTypeType() {

    }

    public TestTypeType(TestType tt) {
        this.testtype = tt.testtype();
        this.subtype1 = tt.subtype1();
        this.subtype2 = tt.subtype2();
    }

    public DefTypes.TestTypes getTesttype() {
        return testtype;
    }

    public void setTesttype(DefTypes.TestTypes testtype) {
        this.testtype = testtype;
    }

    public DefTypes.Subtypes getSubtype1() {
        return subtype1;
    }

    public void setSubtype1(DefTypes.Subtypes subtype1) {
        this.subtype1 = subtype1;
    }

    public DefTypes.Subtypes getSubtype2() {
        return subtype2;
    }

    public void setSubtype2(DefTypes.Subtypes subtype2) {
        this.subtype2 = subtype2;
    }

    @Override
    public DefTypes.TestTypes testtype() {
        return this.testtype;
    }

    @Override
    public DefTypes.Subtypes subtype1() {
        return this.subtype1;
    }

    @Override
    public DefTypes.Subtypes subtype2() {
        return this.subtype2;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return TestTypeType.class;
    }
}
