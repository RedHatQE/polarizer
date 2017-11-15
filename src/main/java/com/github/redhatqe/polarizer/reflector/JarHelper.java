package com.github.redhatqe.polarizer.reflector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.configuration.data.Broker;
import com.github.redhatqe.polarizer.configuration.data.BrokerConfig;
import com.github.redhatqe.polarizer.configuration.data.Serializer;
import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.metadata.Meta;
import com.github.redhatqe.polarizer.metadata.MetaData;
import com.github.redhatqe.polarizer.metadata.TestDefAdapter;
import com.github.redhatqe.polarizer.metadata.TestDefinition;
import com.github.redhatqe.polarizer.processor.TestDefinitionProcessor;
import com.github.redhatqe.polarizer.utils.IJarHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.json.JsonObject;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by stoner on 3/9/16.
 *
 * Takes a jar file from the classpath,
 */
public class JarHelper implements IJarHelper {
    final List<URL> jarPaths;
    final String paths;
    public String testcaseCfgPath;
    public String brokerCfgPath;
    public TestCaseConfig tcConfig;
    public BrokerConfig brokerConfig;
    public File mappingFile;

    public JarHelper(String jarpaths) throws IOException {
        this(jarpaths, TestCaseConfig.getDefaultConfigPath(), BrokerConfig.getDefaultConfigPath(), null);
    }

    public JarHelper(String jarpaths, String testcaseCfgPath) throws IOException {
        this(jarpaths, testcaseCfgPath, BrokerConfig.getDefaultConfigPath(), null);
    }

    public JarHelper(String jarpaths, String testcaseCfgPath, String mapPath) throws IOException {
        this(jarpaths, testcaseCfgPath, BrokerConfig.getDefaultConfigPath(), mapPath);
    }

    public JarHelper( String jarpaths
                    , String testcaseCfgPath
                    , String brokerCfgPath
                    , String mappingPath) throws IOException {
        this.testcaseCfgPath = testcaseCfgPath;
        this.tcConfig = Serializer.fromYaml(TestCaseConfig.class, new File(testcaseCfgPath));
        this.jarPaths = IJarHelper.convertToUrl(jarpaths);
        this.tcConfig.setPathToJar(jarpaths);
        // Converts from file:///path/to/file to /path/to/file
        this.paths = this.jarPaths.stream()
                .map(URL::getFile)
                .reduce("", (i, c) -> c + "," + i);
        this.brokerCfgPath = brokerCfgPath;
        this.brokerConfig = Serializer.fromYaml(BrokerConfig.class, new File(this.brokerCfgPath));
        this.mappingFile = this.makeMapFile(mappingPath);
    }

    private File makeMapFile(String mappingPath) throws IOException {
        File mapPath;
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-rw----");
        FileAttribute<Set<PosixFilePermission>> fp = PosixFilePermissions.asFileAttribute(perms);
        if (mappingPath == null)
            mapPath = Files.createTempFile(Paths.get("/tmp"), "mapping", ".json", fp).toFile();
        else
            mapPath = new File(mappingPath);
        return mapPath;
    }

    public Reflector makeReflector() {
        return new Reflector(this.testcaseCfgPath, this.brokerCfgPath);
    }


    @Override
    public URLClassLoader makeLoader() {
        List<URL> urls = this.jarPaths;
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }


    public Reflector loadClasses(List<String> classes) {
        URLClassLoader ucl = this.makeLoader();
        Reflector refl = this.makeReflector();

        for(String s: classes) {
            try {
                Class<?> cls = ucl.loadClass(s);
                refl.getAnnotations(cls);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return refl;
    }


    public static Set<String> getEnabledTests(List<MetaData> anns) {
        return anns.stream()
                .filter(a -> a.enabled)
                .map(a -> a.className + "." + a.methodName)
                .collect(Collectors.toSet());
    }


    /**
     * This method will figure out
     *
     * @param jarPath path to jar file to reflect on
     * @param tcCfgPath path to a yaml file corresponding to a TestCaseConfig
     * @param mappingPath nullable.  Path to where the mapping.json file is
     */
    public static JsonObject reflect(String jarPath, String tcCfgPath, String mappingPath) throws IOException {
        JarHelper jh = new JarHelper(jarPath, tcCfgPath, mappingPath);
        TestCaseConfig req = jh.tcConfig;
        List<String> packNames = req.getPackages();

        /*
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Type metaType = new TypeToken<List<Meta<TestDefAdapter>>>() {}.getType();
        Type tToC = new TypeToken<Map<String, List<MetaData>>>() {}.getType();
        Type testT = new TypeToken<List<MetaData>>(){}.getType();
        */

        JsonObject jo = new JsonObject();
        try {
            List<String> classes = new ArrayList<>();
            for(String s: jh.paths.split(",")) {
                for(String pn: packNames){
                    classes.addAll(IJarHelper.getClasses(s, pn));
                }

                Reflector refl = jh.loadClasses(classes);
                refl.methToProjectDef = refl.makeMethToProjectMeta();
                refl.processTestDefs();

                List<Optional<ObjectNode>> toBeImported = refl.testcasesImporterRequest(jh.mappingFile);
                TestDefinitionProcessor.writeMapFile(jh.mappingFile, refl.mappingFile);

                refl.testDefAdapters = refl.testDefs.stream()
                        .map(m -> {
                            TestDefinition def = m.annotation;
                            TestDefAdapter adap = TestDefAdapter.create(def);
                            Meta<TestDefAdapter> meta = Meta.create(m.qualifiedName, m.methName, m.className,
                                    m.packName, m.project, m.polarionID, m.params, adap);
                            return meta;
                        })
                        .collect(Collectors.toList());

                /*
                List<Meta<TestDefAdapter>> sorted = Reflector.sortTestDefs(refl.testDefAdapters);
                String jsonDefs = gson.toJson(sorted, metaType);
                String tToCDefs = gson.toJson(refl.testsToClasses, tToC);
                String testng = gson.toJson(refl.methods, testT);

                String output = System.getProperty("user.dir") + "/groups-to-methods.json";
                makeFile(tToCDefs, "/tmp/tests-reflected.json");
                makeFile(jsonDefs, output);
                makeFile(testng, "/tmp/testng-reflected.json");
                */

                Set<String> enabledTests = JarHelper.getEnabledTests(refl.methods);
                Tuple<SortedSet<String>, List<TestDefinitionProcessor.UpdateAnnotation>> audit =
                        TestDefinitionProcessor.auditMethods(enabledTests, refl.methToProjectDef);

                jo = TestDefinitionProcessor.writeAuditJson(null, audit);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jo;
    }
}
