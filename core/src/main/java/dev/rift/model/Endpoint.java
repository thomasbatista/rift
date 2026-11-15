package dev.rift.model;

import java.util.Objects;

/**
 * An operation exposed by the backend, as declared in its API spec.
 *
 * @param method       HTTP method, always uppercase (GET, POST...)
 * @param pathTemplate full path with parameters as {name}, e.g. /api/v2/boards/{boardID}
 */
public record Endpoint(String method, String pathTemplate) {

    public Endpoint {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(pathTemplate, "pathTemplate");
        method = method.toUpperCase();
    }
}