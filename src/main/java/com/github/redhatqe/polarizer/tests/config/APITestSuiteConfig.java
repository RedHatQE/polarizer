package com.github.redhatqe.polarizer.tests.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class APITestSuiteConfig implements Validator {
    @JsonProperty(required = true)
    private XUnitArgs xunit;
    @JsonProperty(required = true)
    private TestCaseArgs testcase;

    public APITestSuiteConfig() {

    }

    public Boolean validate() {
        List<Boolean> valid = new ArrayList<>();
        valid.add(this.xunit.validate());
        valid.add(this.testcase.validate());
        return valid.stream().allMatch(b -> b);
    }

    public XUnitArgs getXunit() {
        return xunit;
    }

    public void setXunit(XUnitArgs xunit) {
        this.xunit = xunit;
    }

    public TestCaseArgs getTestcase() {
        return testcase;
    }

    public void setTestcase(TestCaseArgs testcase) {
        this.testcase = testcase;
    }

    public static void main(String... args) throws IOException {
        File f = new File(args[0]);
        APITestSuiteConfig cfg = Serializer.from(APITestSuiteConfig.class, f);
        System.out.println(cfg.getXunit().getGenerate().getArgs());
    }
}
