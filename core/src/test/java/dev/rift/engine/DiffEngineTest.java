package dev.rift.engine;

import dev.rift.model.ConsumedCall;
import dev.rift.model.ConsumptionGraph;
import dev.rift.model.Endpoint;
import dev.rift.model.Finding;
import dev.rift.model.Rule;
import dev.rift.model.UrlKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffEngineTest {

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET", "/api/projects"),
            new Endpoint("POST", "/api/projects"),
            new Endpoint("GET", "/api/projects/{id}"),
            new Endpoint("GET", "/api/health"));

    @Test
    void aMatchingCallProducesNoMissingFinding() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS,
                graph(call("GET", "/api/projects/{projectId}", UrlKind.TEMPLATE, "a.ts", 3, 0.8)));

        assertTrue(only(findings, Rule.MISSING_ENDPOINT).isEmpty());
    }

    @Test
    void aCallToAnUnknownPathIsMissing() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS,
                graph(call("GET", "/api/reports", UrlKind.LITERAL, "src/api.ts", 12, 1.0)));

        List<Finding> missing = only(findings, Rule.MISSING_ENDPOINT);
        assertEquals(1, missing.size());

        Finding finding = missing.get(0);
        assertEquals("GET", finding.method());
        assertEquals("/api/reports", finding.path());
        assertEquals("src/api.ts", finding.file());
        assertEquals(12, finding.line());
        assertEquals(1.0, finding.confidence(), 1e-9);
        assertEquals("The backend does not declare GET /api/reports", finding.message());
    }

    @Test
    void aKnownPathWithAnotherMethodIsMissingAndSaysWhich() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS,
                graph(call("DELETE", "/api/projects/{id}", UrlKind.TEMPLATE, "a.ts", 9, 0.8)));

        List<Finding> missing = only(findings, Rule.MISSING_ENDPOINT);
        assertEquals(1, missing.size());
        assertEquals("The backend declares /api/projects/{id} only for GET, not for DELETE",
                missing.get(0).message());
    }

    @Test
    void anEndpointNobodyCallsIsAnOrphan() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS, graph(
                call("GET", "/api/projects", UrlKind.LITERAL, "a.ts", 1, 1.0),
                call("POST", "/api/projects", UrlKind.LITERAL, "a.ts", 2, 1.0),
                call("GET", "/api/projects/{id}", UrlKind.TEMPLATE, "a.ts", 3, 0.8)));

        List<Finding> orphans = only(findings, Rule.ORPHAN_ENDPOINT);
        assertEquals(1, orphans.size());
        assertEquals("/api/health", orphans.get(0).path());
        assertEquals(1.0, orphans.get(0).confidence(), 1e-9);
        assertFalse(orphans.get(0).hasLocation());
    }

    @Test
    void dynamicCallsAreIgnoredButLowerTheConfidenceOfOrphans() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS, graph(
                call("GET", "/api/projects", UrlKind.LITERAL, "a.ts", 1, 1.0),
                call("GET", null, UrlKind.DYNAMIC, "b.ts", 5, 0.0)));

        assertTrue(only(findings, Rule.MISSING_ENDPOINT).isEmpty());

        List<Finding> orphans = only(findings, Rule.ORPHAN_ENDPOINT);
        assertEquals(3, orphans.size());
        assertTrue(orphans.stream().allMatch(orphan -> Math.abs(orphan.confidence() - 0.5) < 1e-9));
    }

    @Test
    void withoutAnyCallEveryEndpointIsAnOrphanWithZeroConfidence() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS, graph());

        assertEquals(4, findings.size());
        assertTrue(findings.stream().allMatch(
                finding -> finding.rule() == Rule.ORPHAN_ENDPOINT && finding.confidence() == 0.0));
    }

    @Test
    void aLooseMatchDoesNotHideAnOrphan() {
        List<Endpoint> boards = List.of(
                new Endpoint("GET", "/api/boards/search"),
                new Endpoint("GET", "/api/boards/{boardID}"));

        List<Finding> findings = DiffEngine.analyze(boards,
                graph(call("GET", "/api/boards/{id}", UrlKind.TEMPLATE, "a.ts", 1, 0.8)));

        assertEquals(1, findings.size());
        assertEquals(Rule.ORPHAN_ENDPOINT, findings.get(0).rule());
        assertEquals("/api/boards/search", findings.get(0).path());
    }

    @Test
    void aLooseCandidateWithAnotherMethodIsNotReportedAsAMethodMismatch() {
        List<Endpoint> endpoints = List.of(
                new Endpoint("GET", "/api/x/{teamID}/channels/{channelID}"),
                new Endpoint("GET", "/api/x/{teamID}/boards/{id}"));

        List<Finding> findings = DiffEngine.analyze(endpoints,
                graph(call("POST", "/api/x/{t}/{root}/files", UrlKind.TEMPLATE, "a.ts", 1, 0.8)));

        List<Finding> missing = only(findings, Rule.MISSING_ENDPOINT);
        assertEquals(1, missing.size());
        assertEquals("The backend does not declare POST /api/x/{t}/{root}/files", missing.get(0).message());
    }

    @Test
    void findingsAreOrderedByRuleThenFileThenLine() {
        List<Finding> findings = DiffEngine.analyze(ENDPOINTS, graph(
                call("GET", "/api/zzz", UrlKind.LITERAL, "b.ts", 4, 1.0),
                call("GET", "/api/aaa", UrlKind.LITERAL, "a.ts", 9, 1.0)));

        assertEquals("a.ts", findings.get(0).file());
        assertEquals("b.ts", findings.get(1).file());
        assertEquals(Rule.ORPHAN_ENDPOINT, findings.get(2).rule());
    }

    private static ConsumedCall call(String method, String url, UrlKind kind, String file, int line, double confidence) {
        return new ConsumedCall(method, url, kind, file, line, confidence, List.of());
    }

    private static ConsumptionGraph graph(ConsumedCall... calls) {
        return new ConsumptionGraph(1, "ts", List.of(calls));
    }

    private static List<Finding> only(List<Finding> findings, Rule rule) {
        return findings.stream().filter(finding -> finding.rule() == rule).toList();
    }
}