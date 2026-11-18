package dev.rift;

import dev.rift.loader.ConsumptionGraphLoader;
import dev.rift.loader.OpenApiLoader;
import dev.rift.model.ConsumedCall;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.Endpoint;

import java.nio.file.Path;
import java.util.List;

// Temporary entry point. It will be replaced by the Picocli CLI.
public class Main {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: Main <spec-file> <consumption-graph.json>");
            System.exit(2);
        }

        List<Endpoint> endpoints = OpenApiLoader.load(args[0]);
        ConsumptionGraph graph = ConsumptionGraphLoader.load(Path.of(args[1]));

        long resolved = graph.calls().stream().filter(ConsumedCall::isResolved).count();
        System.out.println(endpoints.size() + " endpoints in the spec");
        System.out.println(graph.calls().size() + " calls in the frontend ("
                + resolved + " resolved, " + (graph.calls().size() - resolved) + " dynamic)");
    }
}