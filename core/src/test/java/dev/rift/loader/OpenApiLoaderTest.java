package dev.rift.loader;

import dev.rift.model.Endpoint;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiLoaderTest {

    @Test
    void swagger2_prefixesBasePath_butNotTwice() throws Exception {
        List<Endpoint> endpoints = OpenApiLoader.load(resource("/specs/swagger2-basepath.yml"));

        assertEquals(List.of(
                new Endpoint("GET", "/api/v2/boards"),
                new Endpoint("POST", "/api/v2/boards"),
                new Endpoint("DELETE", "/api/v2/boards/{boardID}"),
                new Endpoint("GET", "/api/v2/teams/{teamID}/notifyadminupgrade")
        ), endpoints);
    }

    @Test
    void openapi3_usesServerPathAsBasePath() throws Exception {
        List<Endpoint> endpoints = OpenApiLoader.load(resource("/specs/openapi3-server-path.json"));

        assertEquals(List.of(
                new Endpoint("GET", "/api/projects"),
                new Endpoint("PUT", "/api/projects/{id}")
        ), endpoints);
    }

    private static String resource(String name) throws Exception {
        URL url = OpenApiLoaderTest.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("missing test resource: " + name);
        }
        return Path.of(url.toURI()).toString();
    }
}