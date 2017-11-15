package com.github.redhatqe.polarizer.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;


public interface IJarHelper {
    /**
     * Given the path to a jar file, get all the .class files
     * @param jarPath
     * @param pkg
     */
    static List<String> getClasses(String jarPath, String pkg) throws IOException {
       try(ZipFile zf = new ZipFile(jarPath)) {
           return zf.stream()
                   .filter(e -> !e.isDirectory() && e.getName().endsWith(".class") && !e.getName().contains("$"))
                   .map(e -> {
                       String className = e.getName().replace('/', '.');
                       String test = className.substring(0, className.length() - ".class".length());
                       return test;
                   })
                   .filter(e -> e.contains(pkg))
                   .collect(Collectors.toList());
       }
    }

    /**
     * Takes a possibly comma separated string of paths and converts it to a List of URLs
     *
     * @param paths
     * @return
     */
    static List<URL> convertToUrl(String paths) {
        ArrayList<String> jars = new ArrayList<>(Arrays.asList(paths.split(",")));
        List<URL> jarUrls = jars.stream()
                .map(j -> {
                    URL url = null;
                    System.out.println(j);
                    try {
                        url = new URL(j);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    return url;
                })
                .collect(Collectors.toList());
        return jarUrls;
    }

    URLClassLoader makeLoader();
}
