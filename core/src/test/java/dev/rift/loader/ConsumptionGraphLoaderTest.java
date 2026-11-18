package dev.rift.loader;

import dev.rift.model.ConsumedCall;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.UrlKind;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionGraphLoaderTest {

    @Test
    void readsEveryFieldOfACall() throws Exception {
        ConsumptionGraph graph = ConsumptionGraphLoader.load(resource("/graphs/mini-graph.json"));

        assertEquals("ts", graph.extractor());
        assertEquals(3, graph.calls().size());

        ConsumedCall first = graph.calls().get(0);
        assertEquals("GET", first.method());
        assertEquals("/api/projects/{id}", first.url());
        assertEquals(UrlKind.TEMPLATE, first.urlKind());
        assertEquals("src/api/projects.ts", first.file());
        assertEquals(12, first.line());
        assertEquals(0.8, first.confidence(), 1e-9);
        assertEquals(List.of("name", "owner"), first.fieldsRead());
    }

    @Test
    void dynamicCallsHaveNoUrlAndAreNotResolved() throws Exception {
        ConsumptionGraph graph = ConsumptionGraphLoader.load(resource("/graphs/mini-graph.json"));

        ConsumedCall dynamic = graph.calls().get(2);
        assertNull(dynamic.url());
        assertEquals(UrlKind.DYNAMIC, dynamic.urlKind());
        assertFalse(dynamic.isResolved());
        assertTrue(graph.calls().get(0).isResolved());
    }

    @Test
    void rejectsAnUnsupportedVersion() throws Exception {
        Path file = resource("/graphs/wrong-version.json");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> ConsumptionGraphLoader.load(file));
        assertTrue(error.getMessage().contains("version 2"));
    }

    private static Path resource(String name) throws Exception {
        URL url = ConsumptionGraphLoaderTest.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("missing test resource: " + name);
        }
        return Path.of(url.toURI());
    }
}