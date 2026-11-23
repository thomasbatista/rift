package dev.rift.model;

import java.util.Objects;

/**
 * A difference between what the backend declares and what the frontend uses.
 *
 * @param rule       which rule produced it
 * @param method     HTTP method, uppercase
 * @param path       path template involved
 * @param file       frontend source file, or null when the finding has no frontend location
 * @param line       line in {@code file}, or 0 when there is none
 * @param confidence 0.0 to 1.0, how sure rift is that this is a real problem
 * @param message    human readable explanation
 */
public record Finding(Rule rule, String method, String path, String file, int line, double confidence, String message) {

    public Finding {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    public boolean hasLocation() {
        return file != null;
    }

    /** Same finding with {@code prefix/} in front of the file path. Findings without a file are unchanged. */
    public Finding withFilePrefix(String prefix) {
        if (file == null || prefix == null || prefix.isEmpty()) {
            return this;
        }
        return new Finding(rule, method, path, prefix + "/" + file, line, confidence, message);
    }
}