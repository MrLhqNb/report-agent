package com.reportagent.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> handle(Exception e) {
        return Map.of(
            "error", e.getClass().getSimpleName() + ": " + e.getMessage(),
            "success", false
        );
    }
}
