package com.github.redhatqe.polarizer.reflector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.configuration.data.TestCaseConfig;
import com.github.redhatqe.polarizer.metadata.Meta;
import com.github.redhatqe.polarizer.metadata.MetaData;
import com.github.redhatqe.polarizer.metadata.TestDefAdapter;
import com.github.redhatqe.polarizer.metadata.TestDefinition;
import com.github.redhatqe.polarizer.processor.TestDefinitionProcessor;
import com.github.redhatqe.polarizer.utils.IJarHelper;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileAlreadyExistsException;
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
    public String cfgPath;

    public JarHelper(String paths, String cfgPath) {
        this.cfgPath = cfgPath;
        this.jarPaths = IJarHelper.convertToUrl(paths);
        this.paths = this.jarPaths.stream()
                .map(URL::getFile)
                .reduce("", (i, c) -> c + "," + i);
    }


    @Override
    public URLClassLoader makeLoader() {
        List<URL> urls = this.jarPaths;
        return new URLClassLoader(urls.toArray(new URL[urls.size()]));
    }


    public Reflector loadClasses(List<String> classes) {
        URLClassLoader ucl = this.makeLoader();
        Reflector refl;
        if (this.cfgPath != null && !this.cfgPath.equals(""))
            refl = new Reflector(this.cfgPath);
        else
            refl = new Reflector();

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

    static private void makeFile(String json, String filename) {
        File file = new File(filename);

        try {
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    throw new FileAlreadyExistsException("Could not delete old file");
                }
            } else {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(json);
            bw.close();
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }
    }


    /**
     * Takes the jars (which must also be on the classpath) and the names of packages
     * eg java -cp sm-0.0.1-SNAPSHOT.jar --jar file:///path/to/sm-0.0.1-SNAPSHOT-standalone.jar \
     * --packages "polarize.cli.tests,polarize.gui.tests"
     * @param args
     */
    public static void reflect(TestCaseConfig req) {
        String jarPathsOpt = req.getPathToJar();
        List<String> packNames = req.getPackages();

        /*
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Type metaType = new TypeToken<List<Meta<TestDefAdapter>>>() {}.getType();
        Type tToC = new TypeToken<Map<String, List<MetaData>>>() {}.getType();
        Type testT = new TypeToken<List<MetaData>>(){}.getType();
        */

        JarHelper jh = new JarHelper(jarPathsOpt, config);
        try {
            List<String> classes = new ArrayList<>();
            for(String s: jh.paths.split(",")) {
                for(String pn: packNames){
                    classes.addAll(IJarHelper.getClasses(s, pn));
                }

                Reflector refl = jh.loadClasses(classes);
                refl.methToProjectDef = refl.makeMethToProjectMeta();
                refl.processTestDefs();

                List<Optional<ObjectNode>> toBeImported = refl.testcasesImporterRequest();
                File mapPath = new File(refl.config.getMapping());
                TestDefinitionProcessor.writeMapFile(mapPath, refl.mappingFile);

                refl.testDefAdapters = refl.testDefs.stream()
                        .map(m -> {
                            TestDefinition def = m.annotation;
                            TestDefAdapter adap = TestDefAdapter.create(def);
                            Meta<TestDefAdapter> meta = Meta.create(m.qualifiedName, m.methName, m.className,
                                    m.packName, m.project, m.polarionID, m.params, adap);
                            return meta;
                        })
                        .collect(Collectors.toList());
                List<Meta<TestDefAdapter>> sorted = Reflector.sortTestDefs(refl.testDefAdapters);

                String jsonDefs = gson.toJson(sorted, metaType);
                String tToCDefs = gson.toJson(refl.testsToClasses, tToC);
                String testng = gson.toJson(refl.methods, testT);

                makeFile(tToCDefs, "/tmp/tests-reflected.json");
                makeFile(jsonDefs, output);
                makeFile(testng, "/tmp/testng-reflected.json");

                Set<String> enabledTests = JarHelper.getEnabledTests(refl.methods);
                Tuple<SortedSet<String>, List<TestDefinitionProcessor.UpdateAnnotation>> audit =
                        TestDefinitionProcessor.auditMethods(enabledTests, refl.methToProjectDef);
                File path = TestDefinitionProcessor.auditFile;
                TestDefinitionProcessor.writeAuditFile(path, audit);
                TestDefinitionProcessor.checkNoMoreRounds(1, refl.pcfg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
