package com.evplatform.libraries.securelogging;

import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.Map;

/**
 * slf4j facade that routes every key-value pair through the {@link Redactor}
 * before it can reach an appender. Services use this instead of calling the
 * logger with raw domain maps.
 */
public final class SafeLogger {

    private final Logger delegate;

    private SafeLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public static SafeLogger of(Logger logger) {
        return new SafeLogger(logger);
    }

    public void info(String message, Map<String, Object> fields) {
        if (delegate.isInfoEnabled()) {
            delegate.info("{}", Redactor.redactJson(toJson(fields)));
        }
    }

    public void warn(String message, Map<String, Object> fields) {
        if (delegate.isWarnEnabled()) {
            delegate.warn("{}", Redactor.redactJson(toJson(fields)));
        }
    }

    public void error(String message, Map<String, Object> fields, Throwable error) {
        if (delegate.isErrorEnabled()) {
            delegate.error("{}", Redactor.redactJson(toJson(fields)), error);
        }
    }

    public void info(String message, Marker marker, Map<String, Object> fields) {
        if (delegate.isInfoEnabled(marker)) {
            delegate.info(marker, "{}", Redactor.redactJson(toJson(fields)));
        }
    }

    private String toJson(Map<String, Object> fields) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(fields == null ? Map.of() : fields);
        } catch (Exception e) {
            // unreachable for map values, but never echo input on failure
            return "{}";
        }
    }
}
