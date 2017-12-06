package com.github.redhatqe.polarizer.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;

import java.io.File;
import java.io.IOException;


public class Serializer {
    public static <T> void toJson(T cfg, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        writer.writeValue(new File(path), cfg);
    }

    public static <T> T fromJson(Class<T> cfg, File json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, cfg);
    }

    public static <T> T fromJson(Class<T> cfg, String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, cfg);
    }

    public static <T> void toYaml(T cfg, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        writer.writeValue(new File(path), cfg);
    }

    public static <T> T fromYaml(Class<T> cfg, File yaml) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return  mapper.readValue(yaml, cfg);
    }

    public static <T> T fromYaml(Class<T> cfg, String yaml) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return  mapper.readValue(yaml, cfg);
    }

    public static <T> void to(T cfg, String path) throws IOException {
        if (path.endsWith(".json"))
            Serializer.toJson(cfg, path);
        else
            Serializer.toYaml(cfg, path);
    }

    public static <T> T from(Class<T> cfg, File data) throws IOException {
        if (data.toString().endsWith(".yml") || data.toString().endsWith(".yaml")) {
            return Serializer.fromYaml(cfg, data);
        }
        else
            return Serializer.fromJson(cfg, data);
    }

    public static <T> T from(Class<T> cfg, String data) throws IOException {
        if (data.endsWith(".yml") || data.endsWith(".yaml")) {
            return Serializer.fromYaml(cfg, data);
        }
        else
            return Serializer.fromJson(cfg, data);
    }

    public static void main(String[] args) throws IOException {
        TestCaseConfig tcfg = Serializer.from(TestCaseConfig.class, new File(args[0]));
        tcfg.setMapping("");
    }
}
