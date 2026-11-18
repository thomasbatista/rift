package dev.rift.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.rift.model.ConsumptionGraph;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Reads the consumption-graph.json written by an extractor. */
public final class ConsumptionGraphLoader {

    private static final int SUPPORTED_VERSION = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            // The extractor writes "template", the enum is TEMPLATE.
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            // A newer extractor may add fields; an older core should still read the ones it knows.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ConsumptionGraphLoader() {
    }

    public static ConsumptionGraph load(Path file) {
        ConsumptionGraph graph;
        try {
            graph = MAPPER.readValue(file.toFile(), ConsumptionGraph.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read consumption graph " + file, e);
        }

        if (graph.version() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("Unsupported consumption graph version " + graph.version()
                    + " in " + file + " (expected " + SUPPORTED_VERSION + ")");
        }
        return graph;
    }
}