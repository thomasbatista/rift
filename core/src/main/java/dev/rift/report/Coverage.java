package dev.rift.report;

import dev.rift.model.ConsumedCall;
import dev.rift.model.ConsumptionGraph;

/**
 * How much of the frontend's HTTP calls rift could read.
 *
 * @param totalCalls    calls the extractor found
 * @param resolvedCalls calls whose URL the extractor could work out
 * @param ratio         resolvedCalls / totalCalls, 0.0 when there are no calls
 */
public record Coverage(int totalCalls, int resolvedCalls, double ratio) {

    public static Coverage of(ConsumptionGraph graph) {
        int total = graph.calls().size();
        int resolved = (int) graph.calls().stream().filter(ConsumedCall::isResolved).count();
        return new Coverage(total, resolved, total == 0 ? 0.0 : (double) resolved / total);
    }
}