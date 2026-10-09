package com.marlowefinch.ops;

import java.util.List;

/** Thrown when one or more query parameters are invalid; carries every problem found. See TODO-232. */
public class InvalidParametersException extends RuntimeException {

    private final List<String> errors;

    public InvalidParametersException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
