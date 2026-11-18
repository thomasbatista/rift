package dev.rift.model;

import java.util.List;
import java.util.Objects;

/**
 * One HTTP call found in the frontend code.
 *
 * @param method     HTTP method, uppercase
 * @param url        path template such as /api/projects/{id}; null when it could not be resolved
 * @param urlKind    how the URL was resolved
 * @param file       source file, relative to the scanned directory
 * @param line       line of the call, starting at 1
 * @param confidence 0.0 to 1.0, how sure the extractor is about method and url
 * @param fieldsRead response fields the code reads (empty until field tracking exists)
 */
public record ConsumedCall(
        String method,
        String url,
        UrlKind urlKind,
        String file,
        int line,
        double confidence,
        List<String> fieldsRead) {

    public ConsumedCall {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(urlKind, "urlKind");
        method = method.toUpperCase();
        fieldsRead = fieldsRead == null ? List.of() : List.copyOf(fieldsRead);
    }

    /** Dynamic calls carry no URL, so the core must never report findings about them. */
    public boolean isResolved() {
        return url != null && urlKind != UrlKind.DYNAMIC;
    }
}