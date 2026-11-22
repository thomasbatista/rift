package dev.rift.engine;

import dev.rift.model.ConsumedCall;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.Endpoint;
import dev.rift.model.Finding;
import dev.rift.model.Rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares what the backend declares with what the frontend calls.
 *
 * <p>Every finding carries a confidence and none is hidden here: deciding how much doubt is
 * acceptable is up to whoever shows the report.
 */
public final class DiffEngine {

    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::rule)
            .thenComparing(finding -> finding.file() == null ? "" : finding.file())
            .thenComparingInt(Finding::line)
            .thenComparing(Finding::path)
            .thenComparing(Finding::method);

    private DiffEngine() {
    }

    public static List<Finding> analyze(List<Endpoint> endpoints, ConsumptionGraph graph) {
        List<Finding> findings = new ArrayList<>();
        Set<Endpoint> used = new HashSet<>();

        List<ConsumedCall> resolvedCalls = graph.calls().stream().filter(ConsumedCall::isResolved).toList();
        for (ConsumedCall call : resolvedCalls) {
            List<Endpoint> sameMethod = endpoints.stream()
                    .filter(endpoint -> endpoint.method().equals(call.method()))
                    .toList();

            List<Endpoint> matches = PathMatcher.bestMatches(call.url(), sameMethod);
            if (matches.isEmpty()) {
                findings.add(missing(call, endpoints));
            } else {
                used.addAll(matches);
            }
        }

        // The less of the frontend we understood, the likelier an "unused" endpoint is used by
        // a call we could not read.
        double understood = graph.calls().isEmpty() ? 0.0 : (double) resolvedCalls.size() / graph.calls().size();
        for (Endpoint endpoint : endpoints) {
            if (!used.contains(endpoint)) {
                findings.add(orphan(endpoint, understood));
            }
        }

        findings.sort(ORDER);
        return findings;
    }

    private static Finding missing(ConsumedCall call, List<Endpoint> endpoints) {
        List<String> otherMethods = PathMatcher.bestMatches(call.url(), endpoints).stream()
                .map(Endpoint::method)
                .distinct()
                .sorted()
                .toList();

        String message = otherMethods.isEmpty()
                ? "The backend does not declare " + call.method() + " " + call.url()
                : "The backend declares " + call.url() + " only for " + String.join(", ", otherMethods)
                + ", not for " + call.method();

        return new Finding(Rule.MISSING_ENDPOINT, call.method(), call.url(),
                call.file(), call.line(), call.confidence(), message);
    }

    private static Finding orphan(Endpoint endpoint, double confidence) {
        return new Finding(Rule.ORPHAN_ENDPOINT, endpoint.method(), endpoint.pathTemplate(),
                null, 0, confidence,
                "No call in the scanned frontend code reaches " + endpoint.method() + " " + endpoint.pathTemplate());
    }
}