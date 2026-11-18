package dev.rift.model;

import java.util.List;

/** The whole file produced by an extractor. See docs/consumption-graph.md. */
public record ConsumptionGraph(int version, String extractor, List<ConsumedCall> calls) {

    public ConsumptionGraph {
        calls = calls == null ? List.of() : List.copyOf(calls);
    }
}