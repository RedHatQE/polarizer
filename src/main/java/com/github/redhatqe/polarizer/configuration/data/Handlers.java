package com.github.redhatqe.polarizer.configuration.data;



import java.util.Map;

public interface Handlers<T> {
    Map<String, Setter<T>> getHandler();
}
