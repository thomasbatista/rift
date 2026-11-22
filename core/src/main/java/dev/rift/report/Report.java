package dev.rift.report;

import dev.rift.model.Finding;

import java.util.List;

/**
 * Everything a reporter needs to show.
 *
 * @param spec           where the backend spec was read from
 * @param coverage       how much of the frontend was understood
 * @param minConfidence  findings below this confidence are not in {@code findings}
 * @param hiddenFindings how many findings were left out because of {@code minConfidence}
 * @param findings       the findings to show
 */
public record Report(String spec, Coverage coverage, double minConfidence, int hiddenFindings, List<Finding> findings) {

    public Report {
        findings = List.copyOf(findings);
    }
}