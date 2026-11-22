package dev.rift.report;

import dev.rift.model.Finding;
import dev.rift.model.Rule;

import java.util.List;
import java.util.Locale;

/** Renders a {@link Report} for people reading a terminal. */
public final class TextReporter {

    private static final String NL = System.lineSeparator();
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";

    private final boolean color;

    public TextReporter(boolean color) {
        this.color = color;
    }

    public String render(Report report) {
        StringBuilder out = new StringBuilder();
        Coverage coverage = report.coverage();

        out.append(paint(BOLD, "rift")).append("  ").append(report.spec()).append(NL);
        out.append(String.format(Locale.ROOT, "Frontend calls read: %d of %d (%.0f%%)",
                coverage.resolvedCalls(), coverage.totalCalls(), coverage.ratio() * 100)).append(NL);

        for (Rule rule : Rule.values()) {
            List<Finding> findings = report.findings().stream().filter(f -> f.rule() == rule).toList();
            if (findings.isEmpty()) {
                continue;
            }

            String shade = rule == Rule.MISSING_ENDPOINT ? RED : YELLOW;
            out.append(NL).append(paint(shade, rule.name() + " (" + findings.size() + ")")).append(NL);
            for (Finding finding : findings) {
                out.append(line(finding, rule)).append(NL);
            }
        }

        out.append(NL).append(summary(report)).append(NL);
        return out.toString();
    }

    private String line(Finding finding, Rule rule) {
        StringBuilder line = new StringBuilder();
        line.append("  ").append(String.format("%-6s", finding.method())).append(' ').append(finding.path());
        if (finding.hasLocation()) {
            line.append("   ").append(finding.file()).append(':').append(finding.line());
        }
        line.append("   ").append(paint(DIM, String.format(Locale.ROOT, "[%.2f]", finding.confidence())));

        // An orphan's message only repeats the line above. A missing endpoint's message explains it.
        if (rule == Rule.MISSING_ENDPOINT) {
            line.append(NL).append("      ").append(finding.message());
        }
        return line.toString();
    }

    private String summary(Report report) {
        long missing = report.findings().stream().filter(f -> f.rule() == Rule.MISSING_ENDPOINT).count();
        long orphans = report.findings().stream().filter(f -> f.rule() == Rule.ORPHAN_ENDPOINT).count();

        StringBuilder out = new StringBuilder();
        if (missing + orphans == 0) {
            out.append(paint(GREEN, "No drift found.")).append(NL);
        } else {
            out.append(missing).append(" missing, ").append(orphans).append(" orphan.").append(NL);
        }

        if (report.hiddenFindings() > 0) {
            out.append(String.format(Locale.ROOT,
                    "%d finding(s) below confidence %.2f hidden (use --min-confidence 0 to see them).",
                    report.hiddenFindings(), report.minConfidence())).append(NL);
        }

        int unresolved = report.coverage().totalCalls() - report.coverage().resolvedCalls();
        if (unresolved > 0) {
            out.append(unresolved).append(" frontend call(s) could not be resolved and were not checked.").append(NL);
        }
        return out.toString().stripTrailing();
    }

    private String paint(String style, String text) {
        return color ? style + text + RESET : text;
    }
}