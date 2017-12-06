package com.github.redhatqe.polarizer.configuration.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class TestCaseImportResult {
    @JsonProperty(value="import-testcases")
    private List<TestCaseImportInfo> importTestCases;
    @JsonProperty
    private String status;
    @JsonProperty(value="log-url")
    private String logUrl;

    public List<TestCaseImportInfo> getImportTestCases() {
        return importTestCases;
    }

    public void setImportTestCases(List<TestCaseImportInfo> importTestCases) {
        this.importTestCases = importTestCases;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLogUrl() {
        return logUrl;
    }

    public void setLogUrl(String logUrl) {
        this.logUrl = logUrl;
    }

    public static void main(String[] args) {
        String json = "{ \"root\": " +
                "{\n" +
                "  \"import-testcases\" : [ {\n" +
                "    \"name\" : \"RHSM-TC: com.github.redhatqe.rhsm.testpolarize.TestReq.testTestDefinition\",\n" +
                "    \"status\" : \"passed\",\n" +
                "    \"id\" : \"PLATTP-10547\"\n" +
                "  }, {\n" +
                "    \"name\" : \"RHSM-TC: com.github.redhatqe.rhsm.testpolarize.TestPolarize.yetAnotherTestMethod\",\n" +
                "    \"status\" : \"passed\",\n" +
                "    \"id\" : \"PLATTP-10549\"\n" +
                "  } ],\n" +
                "  \"status\" : \"passed\",\n" +
                "  \"log-url\" : \"http://ops-qe-logstash-2.rhev-ci-vms.eng.rdu2.redhat.com:9981/polarion/PLATTP/00000019.log\"\n" +
                "}" +
                "}";
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.reader().readTree(json);
            TestCaseImportResult res = mapper.convertValue(node, TestCaseImportResult.class);
            System.out.println(res.status);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
