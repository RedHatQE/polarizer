package com.github.redhatqe.polarizer.configuration.api;



import java.util.Map;

public interface Handlers<T> {
    Map<String, Setter<T>> getHandler();
}
