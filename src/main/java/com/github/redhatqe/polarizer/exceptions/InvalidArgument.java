package com.github.redhatqe.polarizer.exceptions;

/**
 * Created by stoner on 9/29/16.
 */
public class InvalidArgument extends Error {
    public InvalidArgument() {
        super();
    }

    public InvalidArgument(String msg) {
        super(msg);
    }
}
