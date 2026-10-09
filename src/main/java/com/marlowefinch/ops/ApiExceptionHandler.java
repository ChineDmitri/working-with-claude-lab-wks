package com.marlowefinch.ops;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Renders bad query parameters as {@code 400 {"errors": [...]}} (TODO-232, AC-4). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidParametersException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, List<String>> invalidParameters(InvalidParametersException e) {
        return Map.of("errors", e.errors());
    }

    /** Safety net for any parameter still bound to a typed argument. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, List<String>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return Map.of("errors", List.of(e.getName() + " has an invalid value"));
    }
}
