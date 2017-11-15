package com.github.redhatqe.polarizer.reflector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.configuration.data.BrokerConfig;
import com.github.redhatqe.polarizer.configuration.data.Serializer;
import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.importer.testcase.Parameter;
import com.github.redhatqe.polarizer.importer.testcase.Testcase;
import com.github.redhatqe.polarizer.metadata.*;
import com.github.redhatqe.polarizer.processor.TestDefinitionProcessor;
import com.github.redhatqe.polarizer.reporter.IdParams;
import com.github.redhatqe.polarizer.utils.FileHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Reflector {
    public HashMap<String, List<MetaData>> testsToClasses;
    public List<MetaData> methods;
    public List<Meta<TestDefinition>> testDefs;
    public List<Meta<TestDefAdapter>> testDefAdapters;
    private Set<String> testTypes;
    private static Logger logger = LogManager.getLogger(Reflector.class.getSimpleName());
    public Map<Testcase, Meta<TestDefinition>> testCaseToMeta = new HashMap<>();
    public Map<String,
               Map<String, IdParams>> mappingFile;
    public TestCaseConfig tcConfig;
    public BrokerConfig brokerConfig;
    private Map<String, List<Testcase>> tcMap = new HashMap<>();
    public Map<String,
               Map<String, Meta<TestDefinition>>> methToProjectDef;
    public Map<String, String> methodToDesc = new HashMap<>();


    /**
     * Uses the default broker config file
     * @param testcaseCfgPath
     */
    public Reflector(String testcaseCfgPath) {
        this(testcaseCfgPath, BrokerConfig.getDefaultConfigPath());
    }

    public Reflector(String testcaseCfgPath, String brokerCfgPath) {
        try {
            this.tcConfig = Serializer.fromYaml(TestCaseConfig.class, new File(testcaseCfgPath));
            this.brokerConfig = Serializer.fromYaml(BrokerConfig.class, new File(brokerCfgPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.init();
    }

    private void init() {
        testsToClasses = new HashMap<>();
        testTypes = new HashSet<>(Arrays.asList("AcceptanceTests", "Tier1Tests", "Tier2Tests", "Tier3Tests"));
        testDefs = new ArrayList<>();
        mappingFile = FileHelper.loadMapping(new File(tcConfig.getMapping()));
    }

    private <T> List<Meta<TestDefinition>> getTestDefsMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<Method> filtered = meths.stream()
                .filter(m -> m.getAnnotation(TestDefinitions.class) != null)
                .collect(Collectors.toList());
        return filtered.stream().flatMap(m -> this.flatMapTestDefinitions(m, c))
                .filter(meta -> !meta.className.isEmpty() && !meta.methName.isEmpty())
                .collect(Collectors.toList());
    }

    private <T> Stream<Meta<TestDefinition>> flatMapTestDefinitions(Method m, Class<T> c) {
        TestDefinition ann = m.getAnnotation(TestDefinition.class);
        String className = c.getName();
        String pkg = c.getPackage().getName();
        String methName = m.getName();
        String qual = className + "." + methName;
        DefTypes.Project[] projects = ann.projectID();
        String[] polarionIDs = ann.testCaseID();
        if (polarionIDs.length > 0 && polarionIDs.length != projects.length)
            logger.error("Length of projects and polarionIds not the same");

        if (className.contains(".")) {
            String[] split = className.split("\\.");
            className = split[split.length - 1];
        }

        // TODO: This doesnt get clojure param names. Might need to make Reflector and JarHelper
        // in clojure, and get the args that way.
        java.lang.reflect.Parameter[] params = m.getParameters();
        List<Parameter> args = Arrays.stream(params)
                .map(arg -> {
                    Parameter pm = new Parameter();
                    pm.setName(arg.getName());
                    pm.setScope("local");
                    return pm;
                })
                .collect(Collectors.toList());

        List<Meta<TestDefinition>> metas = new ArrayList<>();
        for(int i = 0; i < projects.length; i++) {
            String project = projects[i].toString();
            String id = "";
            Boolean dirty = false;
            try {
                id = polarionIDs[i];
            }
            catch (ArrayIndexOutOfBoundsException ae) {
                dirty = true;
            }
            Meta<TestDefinition> meta = Meta.create(qual, methName, className, pkg, project, id, args, ann);
            meta.dirty = dirty;
            metas.add(meta);
        }
        return metas.stream();
    }

    /**
     * This is the equivalent of  TestDefinitionProcess.makeMetaFromTestDefinition
     *
     * @param c
     * @param <T>
     * @return
     */
    private <T> List<Meta<TestDefinition>> getTestDefMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<Method> filtered = meths.stream()
                        .filter(m -> m.getAnnotation(TestDefinition.class) != null)
                        .collect(Collectors.toList());
        return filtered.stream().flatMap(m -> this.flatMapTestDefinitions(m, c))
                        .filter(meta -> !meta.className.isEmpty() && !meta.methName.isEmpty())
                        .collect(Collectors.toList());
    }

    /**
     * Gets any methods annotated with TestDefinition
     *
     * @param c
     * @param <T>
     * @return
     */
    public <T> List<MetaData> getTestNGMetaData(Class<T> c) {
        Method[] methods = c.getMethods();
        List<Method> meths = new ArrayList<>(Arrays.asList(methods));
        List<MetaData> classMethods =
                meths.stream()
                        .filter(m -> m.getAnnotation(Test.class) != null)
                        .map(m -> {
                            Test ann = m.getAnnotation(Test.class);
                            String desc = ann.description();
                            String className = c.getName();
                            String methName = m.getName();
                            String provider = ann.dataProvider();
                            Boolean isProvider = !provider.isEmpty();
                            Boolean enabled = ann.enabled();
                            String[] groups = ann.groups();
                            String[] dependsOnGroups = ann.dependsOnGroups();
                            String[] dependsOnMethods = ann.dependsOnMethods();
                            //return className + "." + m.getName();
                            return new MetaData( methName, className, desc, enabled, isProvider, provider
                                               , groups, dependsOnGroups, dependsOnMethods);
                        })
                        .filter(e -> !e.className.isEmpty() && !e.methodName.isEmpty())
                        .collect(Collectors.toList());
        return classMethods;
    }

    public static String findDescription(String qualname, List<MetaData> md) {
        Optional<MetaData> methodMD = md.stream()
                .filter(m -> {
                    String fqpn = String.format("%s.%s", m.className, m.methodName);
                    return qualname.equals(fqpn);
                })
                .findFirst();
        return methodMD.map(metaData -> metaData.description).orElseGet(() -> "This is an automated test: " + qualname);
    }

    public <T> void getAnnotations(Class<T> c) {
        List<MetaData> classMethods = this.getTestNGMetaData(c);
        if(this.methods == null)
            this.methods = classMethods;
        else
            this.methods.addAll(classMethods);

        this.methodToDesc = this.methods.stream()
                .reduce(new HashMap<String, String>(), // the identity value (or accumulator)
                        (accum, n) -> {
                            accum.put(n.className + "." + n.methodName, n.description);
                            return accum;
                        },
                        (partial, next) -> {
                            partial.putAll(next);
                            return partial;
                        });

        this.testDefs.addAll(this.getTestDefMetaData(c));
        this.testDefs.addAll(this.getTestDefsMetaData(c));

        // Get the groups from the Test annotation, store it in a set
        Annotation ann = c.getAnnotation(Test.class);
        if (ann == null) return;
        String[] groups = ((Test) ann).groups();
        Set<String> groupSet = new TreeSet<>(Arrays.asList(groups));

        // Get only the groups from testTypes
        groupSet = groupSet.stream()
                .filter(testTypes::contains)
                .collect(Collectors.toSet());

        for (String g : groupSet) {
            if (!this.testsToClasses.containsKey(g)) {
                this.testsToClasses.put(g, classMethods);
            } else {
                this.testsToClasses.get(g).addAll(classMethods);
            }
        }
    }

    public void processTestDefs() {
        File mapPath = new File(this.tcConfig.getMapping());
        this.testDefs.forEach(td ->
                TestDefinitionProcessor.processTC( td
                                                 , this.mappingFile
                                                 , this.testCaseToMeta
                                                 , this.tcMap
                                                 , mapPath
                                                 , this.methodToDesc
                                                 , this.tcConfig));
    }

    Map<String, Map<String, Meta<TestDefinition>>> makeMethToProjectMeta() {
        Map<String, Map<String, Meta<TestDefinition>>> methToProjectMeta = new HashMap<>();
        for(Meta<TestDefinition> meta: this.testDefs) {
            String qual = meta.qualifiedName;
            String project = meta.project;
            Map<String, Meta<TestDefinition>> projToMeta = new HashMap<>();
            projToMeta.put(project, meta);
            methToProjectMeta.put(qual, projToMeta);
        }
        return methToProjectMeta;
    }

    static List<Meta<TestDefAdapter>> sortTestDefs(List<Meta<TestDefAdapter>> defs) {
        List<Meta<TestDefAdapter>> adaps = defs.stream().sorted((d1, d2) -> {
            String qual1 = d1.qualifiedName;
            String qual2 = d2.qualifiedName;
            if (qual1 == null || qual2 == null) {
                String class1 = d1.className;
                String class2 = d2.className;
                String meth1 = d1.methName;
                String meth2 = d2.methName;
                qual1 = String.format("%s.%s", class1, meth1);
                qual2 = String.format("%s.%s", class2, meth2);
            }
            return qual1.compareTo(qual2);
        }).collect(Collectors.toList());
        return adaps;
    }

    List<Optional<ObjectNode>> testcasesImporterRequest(File mapPath) {
        return TestDefinitionProcessor.tcImportRequest(this.tcMap
                , methToProjectDef
                , this.mappingFile
                , mapPath
                , this.tcConfig
                , this.brokerConfig);
    }
}
