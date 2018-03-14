package com.github.redhatqe.polarizer.test.messagebus;


import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.utils.FileHelper;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class ConfigurationTests {
    public String xunit;
    public String xargs;
    public String testcase;
    public String tcargs;

    public ConfigurationTests() {
        String home = Paths.get(System.getProperty("user.home")).toString();
        xunit = Paths.get(home, "Projects/testpolarize/testng-polarion.xml").toString();
        xargs = Paths.get(home, "test-polarizer-xunit.json").toString();
        testcase = Paths.get(home, "Projects/testpolarize/testcases").toString();
        tcargs = Paths.get(home, "test-polarizer-testcase.json").toString();
    }

    @Test(groups={"serialization"},
          description="Tests json message can be serialized for xunit import")
    public void serializeXUnitWebSocket() throws IOException {
        String xunitXml = FileHelper.readFile(this.xunit);
        String xunitArgs = FileHelper.readFile(this.xargs);


    }

    public static void main(String[] args) throws IOException {
        TestCaseConfig cfg = Serializer.from(TestCaseConfig.class, new File(args[0]));
        String defs = cfg.getDefinitionsPath();
    }
}
