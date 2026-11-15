package dev.rift.loader;

import dev.rift.model.Endpoint;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads an API spec (Swagger 2.0 or OpenAPI 3, JSON or YAML) and lists the
 * operations it declares.
 */
public final class OpenApiLoader {

    private OpenApiLoader() {
    }

    public static List<Endpoint> load(String location) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        SwaggerParseResult result = new OpenAPIParser().readLocation(location, null, options);
        OpenAPI api = result.getOpenAPI();
        if (api == null) {
            throw new IllegalArgumentException(
                    "Could not read spec at " + location + ": " + result.getMessages());
        }

        List<Endpoint> endpoints = new ArrayList<>();
        if (api.getPaths() == null) {
            return endpoints;
        }

        String basePath = basePathOf(api);
        for (Map.Entry<String, PathItem> entry : api.getPaths().entrySet()) {
            String fullPath = withBasePath(basePath, entry.getKey());
            for (PathItem.HttpMethod method : entry.getValue().readOperationsMap().keySet()) {
                endpoints.add(new Endpoint(method.name(), fullPath));
            }
        }

        endpoints.sort(Comparator.comparing(Endpoint::pathTemplate).thenComparing(Endpoint::method));
        return endpoints;
    }

    /** Swagger 2.0 "basePath" is converted by the parser into the first server URL. */
    private static String basePathOf(OpenAPI api) {
        if (api.getServers() == null || api.getServers().isEmpty()) {
            return "";
        }
        String url = api.getServers().get(0).getUrl();
        if (url == null) {
            return "";
        }
        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path.replaceAll("/+$", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Adds the base path unless the spec path already starts with it (Focalboard has one such case). */
    private static String withBasePath(String basePath, String path) {
        if (basePath.isEmpty() || path.equals(basePath) || path.startsWith(basePath + "/")) {
            return path;
        }
        return basePath + path;
    }
}