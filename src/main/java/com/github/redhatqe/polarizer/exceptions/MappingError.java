package com.github.redhatqe.polarizer.exceptions;

/**
 * Created by stoner on 10/5/16.
 */
public class MappingError extends Error {
    public MappingError(String err) {
        super(err);
    }

    public MappingError() {
        super();
    }
}
