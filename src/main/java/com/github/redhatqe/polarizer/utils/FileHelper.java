package com.github.redhatqe.polarizer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.redhatqe.polarizer.exceptions.InvalidArgumentType;
import com.github.redhatqe.polarizer.processor.Meta;
import com.github.redhatqe.polarizer.reporter.IdParams;
import io.vertx.reactivex.core.file.FileSystem;
import org.apache.camel.util.StringHelper;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;


public class FileHelper implements IFileHelper {

    /**
     * Creates a Path to look up or create an xml description
     *
     * It uses this pattern:
     *
     * base/[project]/requirements|testcases/[class]/[methodName].xml as the path to the File
     *
     * @param base
     * @param meta
     * @return
     */
    public static <T> Path makeXmlPath(String base, Meta<T> meta, String projID) throws InvalidArgumentType {
        String xmlname;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, projID, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    public static <T> Path makeXmlPath(String base, Meta<T> meta) throws InvalidArgumentType {
        String xmlname;
        String className = meta.className;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, meta.project, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    static public void makeFile(String json, String filename) {
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

    static public File makeTempFile(String dir, String pre, String suff, String perm) {
        if (perm == null)
            perm = "rw-rw----";
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(perm);
        FileAttribute<Set<PosixFilePermission>> fp = PosixFilePermissions.asFileAttribute(perms);

        Path temp;
        try {
            temp = Files.createTempFile(Paths.get("/tmp"), "testcase-import", suff, fp);
        } catch (IOException e) {
            e.printStackTrace();
            temp = new File(String.format("%s/tmp-%s%s", dir, pre, suff)).toPath();
        }
        return temp.toFile();
    }

    public static boolean deleteFile(String path) {
        File f = new File(path);
        return !f.exists() || f.delete();
    }

    public static boolean deleteFile(File f) {
        return !f.exists() || f.delete();
    }

    public static boolean deleteFile(Path path) {
        File f = path.toFile();
        return !f.exists() || f.delete();
    }

    /**
     * From the path pointing to a mapping.json file, create a Map from it
     *
     * The key to the Map is the unique name of a method (usually its fully qualified name), and the value is a Map
     * of the form Project -> IdParams.  This second map is needed because one test method is often used across multiple
     * projects.
     *
     *
     * @param fpath path to a mapping.json file
     * @return an in-memory map of unique methodname -> project -> IdParams
     */
    public static Map<String, Map<String, IdParams>> loadMapping(File fpath) {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Map<String, IdParams>> mapped = new HashMap<>();
        try {
            if (fpath.exists()) {
                // First key is name, second is project, third is "id" or "parameters"
                JsonNode node = mapper.readTree(fpath);
                node.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode projectToInner = entry.getValue();
                    Map<String, IdParams> innerMap = new HashMap<>();
                    projectToInner.fields().forEachRemaining(e -> {
                        String project = e.getKey();
                        JsonNode inner = e.getValue();
                        String id = inner.get("id").toString();

                        IdParams idp = new IdParams();
                        idp.id = StringHelper.removeQuotes(id);
                        JsonNode paramNode = inner.get("parameters");
                        if (paramNode instanceof ArrayNode) {
                            ArrayNode params = (ArrayNode) paramNode;
                            idp.parameters = params.findValuesAsText("parameters");
                            List<String> p = new ArrayList<>();
                            for(int i = 0; i < params.size(); i++) {
                                p.add(StringHelper.removeQuotes(params.get(i).toString()));
                            }
                            idp.parameters = p;
                        }
                        innerMap.put(project, idp);
                    });
                    mapped.put(name, innerMap);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapped;
    }
}
