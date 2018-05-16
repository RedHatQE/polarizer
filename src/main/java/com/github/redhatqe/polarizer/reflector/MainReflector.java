package com.github.redhatqe.polarizer.reflector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.redhatqe.polarize.metadata.TestDefAdapter;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarizer.data.TestCaseNeedingImport;
import com.github.redhatqe.polarizer.messagebus.DefaultResult;
import com.github.redhatqe.polarizer.messagebus.config.BrokerConfig;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.reporter.configuration.Serializer;
import com.github.redhatqe.polarizer.reporter.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.messagebus.MessageResult;
import com.github.redhatqe.polarizer.processor.Meta;
import com.github.redhatqe.polarizer.processor.MetaData;
import com.github.redhatqe.polarizer.processor.MetaProcessor;
import com.github.redhatqe.polarizer.utils.FileHelper;
import com.github.redhatqe.polarizer.utils.IJarHelper;
import com.github.redhatqe.polarizer.utils.Tuple;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
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
public class MainReflector implements IJarHelper {
    final List<URL> jarPaths;
    final String paths;
    private String testcaseCfgPath;
    private String brokerCfgPath;
    private TestCaseConfig tcConfig;
    private BrokerConfig brokerConfig;
    private File mappingFile;

    public MainReflector(String jarpaths) throws IOException {
        this(jarpaths, TestCaseConfig.getDefaultConfigPath(), BrokerConfig.getDefaultConfigPath(), null);
    }

    public MainReflector(String jarpaths, String testcaseCfgPath) throws IOException {
        this(jarpaths, testcaseCfgPath, BrokerConfig.getDefaultConfigPath(), null);
    }

    public MainReflector(String jarpaths, String testcaseCfgPath, String mapPath) throws IOException {
        this(jarpaths, testcaseCfgPath, BrokerConfig.getDefaultConfigPath(), mapPath);
    }

    public MainReflector(String jarpaths
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

    public MainReflector( String jarpaths
                        , TestCaseConfig testcaseCfg
                        , String brokerCfgPath
                        , String mappingPath) throws IOException {
        this.tcConfig = testcaseCfg;
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

    public MainReflector( String jarpaths
            , TestCaseConfig testcaseCfg
            , BrokerConfig brokerCfg
            , String mappingPath) throws IOException {
        this.tcConfig = testcaseCfg;
        this.jarPaths = IJarHelper.convertToUrl(jarpaths);
        this.tcConfig.setPathToJar(jarpaths);
        // Converts from file:///path/to/file to /path/to/file
        this.paths = this.jarPaths.stream()
                .map(URL::getFile)
                .reduce("", (i, c) -> c + "," + i);
        this.brokerConfig = brokerCfg;
        this.mappingFile = this.makeMapFile(mappingPath);
    }

    public MainReflector(TestCaseConfig testcaseCfg) throws IOException {
        this.tcConfig = testcaseCfg;
        this.jarPaths = IJarHelper.convertToUrl(this.tcConfig.getPathToJar());
        // Converts from file:///path/to/file to /path/to/file
        this.paths = this.jarPaths.stream()
                .map(URL::getFile)
                .reduce("", (i, c) -> c + "," + i);
        this.brokerCfgPath = BrokerConfig.getDefaultConfigPath();
        this.brokerConfig = Serializer.fromYaml(BrokerConfig.class, new File(this.brokerCfgPath));
        this.mappingFile = new File(this.tcConfig.getMapping());
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
        Reflector refl;
        if (this.testcaseCfgPath == null || this.testcaseCfgPath.equals(""))
            refl = new Reflector(this.tcConfig);
        else
            refl = new Reflector(this.testcaseCfgPath, this.brokerCfgPath, this.mappingFile);
        return refl;
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
            } catch (NoClassDefFoundError ex) {
                System.err.println("Got a NoClassDefFoundError.  Try uploading a 'fat' or 'uber' jar");
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

    public static Set<String> getImportNotReadyTests(List<Meta<TestDefinition>> anns) {
        return anns.stream()
                .filter(a -> !a.annotation.importReady())
                .map(a -> a.qualifiedName)
                .collect(Collectors.toSet());
    }


    public static Reflector reflect(String jarPath, String tcCfgPath, String mappingPath) throws IOException {
        MainReflector jh = new MainReflector(jarPath, tcCfgPath, mappingPath);
        TestCaseConfig req = jh.tcConfig;
        List<String> packNames = req.getPackages();

        List<String> classes = new ArrayList<>();
        for(String s: jh.paths.split(",")) {
            for (String pn : packNames) {
                classes.addAll(IJarHelper.getClasses(s, pn));
            }
        }
        Reflector refl = jh.loadClasses(classes);
        refl.methToProjectDef = refl.makeMethToProjectMeta();
        refl.setResults(refl.processTestDefs());

        return refl;
    }

    public static Reflector reflect(TestCaseConfig tcfg) throws IOException {
        MainReflector jh = new MainReflector(tcfg);
        TestCaseConfig req = jh.tcConfig;
        List<String> packNames = req.getPackages();

        List<String> classes = new ArrayList<>();
        for(String s: jh.paths.split(",")) {
            for (String pn : packNames) {
                classes.addAll(IJarHelper.getClasses(s, pn));
            }
        }
        Reflector refl = jh.loadClasses(classes);
        refl.methToProjectDef = refl.makeMethToProjectMeta();
        refl.setResults(refl.processTestDefs());

        return refl;
    }

    private static JsonObject _generate(Reflector refl) {
        if (!refl.mapPath.exists())
            refl.mappingFile = MetaProcessor.createMappingFile( refl.mapPath,
                    refl.methToProjectDef, refl.mappingFile);

        List<File> xmlimport = MetaProcessor.generateXML(refl.getTcMap(), refl.getTcConfig());
        List<String> imports = new ArrayList<>();

        JsonObject um = MetaProcessor.updateMappingFile(refl.mappingFile, refl.methToProjectDef, refl.mapPath, null);
        // Open the files, and dump them back
        xmlimport.forEach(xml -> {
            try {
                String content = FileHelper.readFile(xml.toString());
                imports.add(content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        JsonArray xml = new JsonArray(imports);
        um.put("testcase-xml", xml);
        return um;
    }

    private static JsonObject _process(Reflector refl) throws IOException {
        if (!refl.mapPath.exists())
            refl.mappingFile = MetaProcessor.createMappingFile( refl.mapPath,
                    refl.methToProjectDef, refl.mappingFile);

        List<Optional<MessageResult<DefaultResult>>> importResults = refl.testcasesImporterRequest(refl.mapPath);
        JsonObject um = MetaProcessor.updateMappingFile(refl.mappingFile, refl.methToProjectDef, refl.mapPath, null);
        MetaProcessor.writeMapFile(refl.mapPath, refl.mappingFile);

        refl.testDefAdapters = refl.testDefs.stream()
                .map(m -> {
                    TestDefinition def = m.annotation;
                    TestDefAdapter adap = TestDefAdapter.create(def);
                    return Meta.create(m.qualifiedName, m.methName, m.className,
                            m.packName, m.project, m.polarionID, m.params, adap);
                })
                .collect(Collectors.toList());

        // Determine which methods still need to be imported to polarion, which test methods are being used by TestNG
        // but don't have an @TestDefinition, and which methods have update=true in the annotation
        Set<String> importNotReady = MainReflector.getImportNotReadyTests(refl.testDefs);
        Set<String> enabledTests = MainReflector.getEnabledTests(refl.methods);
        Tuple<SortedSet<String>, List<MetaProcessor.UpdateAnnotation>> audit =
                MetaProcessor.auditMethods(enabledTests, refl.methToProjectDef);

        JsonObject jo = MetaProcessor.writeAuditJson(null, audit);

        importResults.forEach(o -> o.ifPresent(mr -> {
            // Add the POST response body
            jo.put("post-body", mr.getBody());
            mr.getNode().ifPresent(n -> {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Object text = mapper.treeToValue(n, Object.class);
                    jo.put("message", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(text));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            jo.put("import-status", mr.getStatus().toString());
            String err = mr.getErrorDetails();
            try {
                TestCaseNeedingImport needs = Serializer.fromJson(TestCaseNeedingImport.class, err);
                jo.put("methods-needing-import", needs != null ? needs.getTitles() : "");
            } catch (IOException e) {
                jo.put("methods-needing-import", new ArrayList<String>());
            }
        }));
        // Add any of the no-ids-funcs
        List<String> ids = new ArrayList<>();
        refl.getResults().forEach(pi -> {
            pi.getNoIdFuncs().stream()
                    .filter(ni -> !ni.equals(""))
                    .peek(System.out::println)
                    .forEach(ids::add);
        });
        jo.put("methods-missing-testCaseID", ids);
        // Add tests which have importReady=false
        jo.put("methods-not-ready-for-import", new ArrayList<>(importNotReady));
        // Add the mapping.json to the returned JSON
        Map<String, Map<String, IdParams>> sortedMapping = new TreeMap<>(refl.mappingFile);
        jo.put("mapping", sortedMapping);


        return jo;
    }

    public static JsonObject process(String jarPath, String tcCfgPath, String mappingPath) throws IOException {
        Reflector refl = reflect(jarPath, tcCfgPath, mappingPath);
        return MainReflector._process(refl);
    }

    public static JsonObject process(TestCaseConfig tcfg) throws IOException {
        Reflector refl = reflect(tcfg);
        return MainReflector._process(refl);
    }

    public static JsonObject generate(String jarPath, String tcCfgPath, String mappingPath) throws IOException {
        Reflector refl = reflect(jarPath, tcCfgPath, mappingPath);
        return MainReflector._generate(refl);
    }

    public static JsonObject generate(TestCaseConfig cfg) throws IOException {
        Reflector refl = reflect(cfg);
        return MainReflector._generate(refl);
    }

    // FIXME: Replace this with a test
    // arg[0] jarpath
    // arg[1] configpath
    // arg[2] mappath
    public static void main(String[] args) throws IOException {
        //JsonObject jo = MainReflector.process(args[0], args[1], args[2]);
        JsonObject jo = MainReflector.process(args[0], args[1], args[2]);
        System.out.println(jo.encodePrettily());
    }
}
