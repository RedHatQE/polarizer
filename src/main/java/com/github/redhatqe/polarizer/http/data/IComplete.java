package com.github.redhatqe.polarizer.http.data;

import java.util.Set;

public interface IComplete {
    Set<String> completed();
    void addToComplete(String s);
}
