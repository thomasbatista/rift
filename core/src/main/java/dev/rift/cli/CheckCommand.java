package dev.rift.cli;

import dev.rift.engine.DiffEngine;
import dev.rift.loader.ConsumptionGraphLoader;
import dev.rift.loader.OpenApiLoader;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.Endpoint;
import dev.rift.model.Finding;
import dev.rift.report.Coverage;
import dev.rift.report.JsonReporter;
import dev.rift.report.Report;
import dev.rift.report.TextReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "check",
        mixinStandardHelpOptions = true,
        description = "Compare the backend spec with the HTTP calls found in the frontend.",
        footer = {
                "",
                "Exit codes: 0 nothing to report, 1 findings, 2 bad usage or unreadable input."
        })
public class CheckCommand implements Callable<Integer> {

    @Spec
    CommandSpec commandSpec;

    @Option(names = "--spec", required = true, paramLabel = "<file|url>",
            description = "Backend API spec: Swagger 2.0 or OpenAPI 3, JSON or YAML.")
    String specLocation;

    @Option(names = "--consumption", required = true, paramLabel = "<file>",
            description = "consumption-graph.json written by an extractor.")
    Path consumption;

    @Option(names = "--min-confidence", defaultValue = "0.8", paramLabel = "<0..1>",
            description = "Hide findings below this confidence; 0 shows everything. Default: ${DEFAULT-VALUE}.")
    double minConfidence;

    @Option(names = "--format", defaultValue = "text", paramLabel = "<format>",
            description = "Output format: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    Format format;

    @Option(names = "--fail-on", defaultValue = "missing", paramLabel = "<what>",
            description = "What makes the exit code 1: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    FailOn failOn;

    @Option(names = "--no-color", description = "Never use colors (NO_COLOR is also respected).")
    boolean noColor;

    @Override
    public Integer call() {
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("--min-confidence must be between 0 and 1");
        }

        List<Endpoint> endpoints = OpenApiLoader.load(specLocation);
        ConsumptionGraph graph = ConsumptionGraphLoader.load(consumption);

        List<Finding> all = DiffEngine.analyze(endpoints, graph);
        List<Finding> shown = all.stream().filter(finding -> finding.confidence() >= minConfidence).toList();
        Report report = new Report(specLocation, Coverage.of(graph), minConfidence, all.size() - shown.size(), shown);

        String output = format == Format.JSON
                ? new JsonReporter().render(report)
                : new TextReporter(useColor()).render(report);

        PrintWriter out = commandSpec.commandLine().getOut();
        out.println(output);
        out.flush();

        return shown.stream().anyMatch(failOn::fails) ? 1 : 0;
    }

    private boolean useColor() {
        return !noColor && System.getenv("NO_COLOR") == null && System.console() != null;
    }
}