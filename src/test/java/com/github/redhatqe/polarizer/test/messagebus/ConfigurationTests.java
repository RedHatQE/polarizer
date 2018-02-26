package com.github.redhatqe.polarizer.test.messagebus;


import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;

import java.io.File;
import java.io.IOException;

public class ConfigurationTests {
    public static void main(String[] args) throws IOException {
        TestCaseConfig cfg = Serializer.from(TestCaseConfig.class, new File(args[0]));
        String defs = cfg.getDefinitionsPath();
    }
}
