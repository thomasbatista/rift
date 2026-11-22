package dev.rift;

import dev.rift.engine.DiffEngine;
import dev.rift.loader.ConsumptionGraphLoader;
import dev.rift.loader.OpenApiLoader;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.Endpoint;
import dev.rift.model.Finding;
import dev.rift.model.Rule;

import java.nio.file.Path;
import java.util.List;

// Temporary entry point. It will be replaced by the Picocli CLI.
public class Main {

    private static final double MIN_CONFIDENCE = 0.8;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: Main <spec-file> <consumption-graph.json>");
            System.exit(2);
        }

        List<Endpoint> endpoints = OpenApiLoader.load(args[0]);
        ConsumptionGraph graph = ConsumptionGraphLoader.load(Path.of(args[1]));
        List<Finding> findings = DiffEngine.analyze(endpoints, graph);

        System.out.println(endpoints.size() + " endpoints, " + graph.calls().size() + " frontend calls");

        for (Rule rule : Rule.values()) {
            List<Finding> shown = findings.stream()
                    .filter(finding -> finding.rule() == rule && finding.confidence() >= MIN_CONFIDENCE)
                    .toList();

            System.out.println();
            System.out.println(rule + " (" + shown.size() + ")");
            for (Finding finding : shown) {
                String where = finding.hasLocation() ? "   " + finding.file() + ":" + finding.line() : "";
                System.out.println("  " + finding.method() + " " + finding.path() + where);
            }
        }
    }
}