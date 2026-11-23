package dev.rift.report;

import dev.rift.model.Finding;
import dev.rift.model.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubReporterTest {

    private static final Finding MISSING = new Finding(Rule.MISSING_ENDPOINT, "PUT", "/api/projects/{id}",
            "frontend/src/api.ts", 17, 0.8,
            "The backend declares /api/projects/{id} only for GET, PATCH, not for PUT");

    private static final Finding ORPHAN = new Finding(Rule.ORPHAN_ENDPOINT, "GET", "/api/health",
            null, 0, 5 / 6.0, "No call in the scanned frontend code reaches GET /api/health");

    private static final Coverage COVERAGE = new Coverage(6, 5, 5 / 6.0);

    private static Report report(Finding... findings) {
        return new Report("openapi.json", COVERAGE, 0.8, 0, List.of(findings));
    }

    @Test
    void aFindingThatFailsTheBuildIsAnError() {
        String output = new GithubReporter(finding -> true).render(report(MISSING));

        assertEquals("::error file=frontend/src/api.ts,line=17,title=rift MISSING_ENDPOINT::"
                        + "The backend declares /api/projects/{id} only for GET, PATCH, not for PUT",
                output.lines().findFirst().orElseThrow());
    }

    @Test
    void aFindingThatDoesNotFailTheBuildIsAWarning() {
        String output = new GithubReporter(finding -> false).render(report(MISSING));

        assertTrue(output.startsWith("::warning file=frontend/src/api.ts,line=17,"));
    }

    @Test
    void endpointsWithoutALocationGoIntoACollapsedGroup() {
        String output = new GithubReporter(finding -> true).render(report(ORPHAN));

        assertTrue(output.contains("::group::Endpoints no scanned frontend code calls (1)\n"
                + "GET    /api/health   [0.83]\n"
                + "::endgroup::\n"));
        assertFalse(output.contains("::error"), "an endpoint without a file cannot be annotated");
        assertFalse(output.contains("::warning"));
    }

    @Test
    void theNoticeSummarizesCoverageAndFindings() {
        String output = new GithubReporter(finding -> true).render(report(MISSING, ORPHAN));

        // GitHub reads %25 back as a percent sign.
        assertTrue(output.contains("::notice title=rift::1 missing, 1 orphan. Frontend calls read: 5 of 6 (83%25)."));
    }

    @Test
    void specialCharactersAreEscaped() {
        Finding tricky = new Finding(Rule.MISSING_ENDPOINT, "GET", "/a", "dir,1/a:b.ts", 3, 1.0,
                "100% sure\nsecond line");

        String output = new GithubReporter(finding -> true).render(report(tricky));

        assertEquals("::error file=dir%2C1/a%3Ab.ts,line=3,title=rift MISSING_ENDPOINT::100%25 sure%0Asecond line",
                output.lines().findFirst().orElseThrow());
    }

    @Test
    void aCleanReportSaysThereIsNoDrift() {
        Report clean = new Report("openapi.json", new Coverage(4, 4, 1.0), 0.8, 2, List.of());

        assertEquals("::notice title=rift::No drift found. Frontend calls read: 4 of 4 (100%25). "
                        + "2 finding(s) below confidence 0.80 hidden.\n",
                new GithubReporter(finding -> true).render(clean));
    }
}