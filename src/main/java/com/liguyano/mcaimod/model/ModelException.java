package com.liguyano.mcaimod.model;

/** Deliberately excludes response bodies and credentials. */
public final class ModelException extends RuntimeException {
    public ModelException(String message) {
        super(message);
    }
}
