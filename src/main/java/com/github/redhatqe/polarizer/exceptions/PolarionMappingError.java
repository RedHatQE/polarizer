package com.github.redhatqe.polarizer.exceptions;


/**
 * Created by stoner on 8/3/16.
 */
public class PolarionMappingError extends Error {
    public PolarionMappingError(String err) {
        super(err);
    }

    public PolarionMappingError() {
        super();
    }
}
