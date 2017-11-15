package com.github.redhatqe.polarizer.configuration.data;


import java.io.IOException;

/**
 * This interface is meant to be implemented by any class that represents the underlying data.  
 */
public interface IConfig {
    /**
     * This method will write the java object to a file
     * @param path
     */
    default void writeConfig(String path) {
        if (path.endsWith(".yaml"))
            try {
                Serializer.toYaml(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        else if (path.endsWith(".json"))
            try {
                Serializer.toJson(this, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
    }

}
