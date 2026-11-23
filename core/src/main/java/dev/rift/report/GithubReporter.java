package dev.rift.report;

import dev.rift.model.Finding;
import dev.rift.model.Rule;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Renders a {@link Report} as GitHub Actions workflow commands.
 *
 * <p>A finding with a location becomes an annotation on that file and line: an error when it makes
 * the build fail, a warning otherwise. Findings without a location (endpoints nobody calls) go into
 * a collapsed log group, because GitHub shows only the first few annotations of each kind.
 */
public final class GithubReporter {

    private static final String NL = "\n";

    private final Predicate<Finding> failsTheBuild;

    public GithubReporter(Predicate<Finding> failsTheBuild) {
        this.failsTheBuild = failsTheBuild;
    }

    public String render(Report report) {
        StringBuilder out = new StringBuilder();

        for (Finding finding : report.findings()) {
            if (!finding.hasLocation()) {
                continue;
            }
            String level = failsTheBuild.test(finding) ? "error" : "warning";
            out.append("::").append(level)
                    .append(" file=").append(property(finding.file()))
                    .append(",line=").append(finding.line())
                    .append(",title=").append(property("rift " + finding.rule().name()))
                    .append("::").append(data(finding.message()))
                    .append(NL);
        }

        List<Finding> withoutLocation = report.findings().stream().filter(f -> !f.hasLocation()).toList();
        if (!withoutLocation.isEmpty()) {
            out.append("::group::Endpoints no scanned frontend code calls (").append(withoutLocation.size()).append(")").append(NL);
            for (Finding finding : withoutLocation) {
                out.append(String.format(Locale.ROOT, "%-6s %s   [%.2f]", finding.method(), finding.path(), finding.confidence()))
                        .append(NL);
            }
            out.append("::endgroup::").append(NL);
        }

        out.append("::notice title=rift::").append(data(summary(report))).append(NL);
        return out.toString();
    }

    private static String summary(Report report) {
        long missing = report.findings().stream().filter(f -> f.rule() == Rule.MISSING_ENDPOINT).count();
        long orphans = report.findings().stream().filter(f -> f.rule() == Rule.ORPHAN_ENDPOINT).count();
        Coverage coverage = report.coverage();

        StringBuilder summary = new StringBuilder();
        summary.append(missing + orphans == 0 ? "No drift found." : missing + " missing, " + orphans + " orphan.");
        summary.append(String.format(Locale.ROOT, " Frontend calls read: %d of %d (%.0f%%).",
                coverage.resolvedCalls(), coverage.totalCalls(), coverage.ratio() * 100));
        if (report.hiddenFindings() > 0) {
            summary.append(String.format(Locale.ROOT, " %d finding(s) below confidence %.2f hidden.",
                    report.hiddenFindings(), report.minConfidence()));
        }
        return summary.toString();
    }

    /** Escaping for the text after "::" in a workflow command. */
    private static String data(String value) {
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }

    /** Escaping for values such as file= and title=, which also end at ":" and ",". */
    private static String property(String value) {
        return data(value).replace(":", "%3A").replace(",", "%2C");
    }
}