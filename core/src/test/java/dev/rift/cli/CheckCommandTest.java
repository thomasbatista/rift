package dev.rift.cli;

import dev.rift.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckCommandTest {

    private record Result(int exitCode, String out, String err) {
    }

    @Test
    void driftIsReportedAndFailsTheBuild() throws Exception {
        Result result = run(check("drift-graph.json"));

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("MISSING_ENDPOINT (1)"));
        assertTrue(result.out().contains("ORPHAN_ENDPOINT (2)"));
        assertTrue(result.out().contains("/api/v2/reports"));
        assertTrue(result.out().contains("hidden"));
        assertFalse(result.out().contains("/archive"), "the 0.5 confidence finding must be hidden by default");
    }

    @Test
    void lowerConfidenceFindingsAppearWhenAsked() throws Exception {
        Result result = run(check("drift-graph.json", "--min-confidence", "0"));

        assertTrue(result.out().contains("/archive"));
    }

    @Test
    void failOnNeverKeepsTheExitCodeAtZero() throws Exception {
        assertEquals(0, run(check("drift-graph.json", "--fail-on", "never")).exitCode());
        assertEquals(0, run(check("drift-graph.json", "--fail-on", "NEVER")).exitCode());
    }

    @Test
    void aCleanProjectPasses() throws Exception {
        Result result = run(check("clean-graph.json"));

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("No drift found."));
    }

    @Test
    void jsonFormatIsWrittenForOtherPrograms() throws Exception {
        Result result = run(check("drift-graph.json", "--format", "json"));

        assertTrue(result.out().trim().startsWith("{"));
        assertTrue(result.out().contains("\"MISSING_ENDPOINT\""));
        assertTrue(result.out().contains("\"hiddenFindings\""));
    }

    @Test
    void anUnreadableInputExitsWithTwo() throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "check", "--spec", resource("/specs/swagger2-basepath.yml"),
                "--consumption", "does-not-exist.json"));

        Result result = run(args.toArray(new String[0]));

        assertEquals(2, result.exitCode());
        assertTrue(result.err().startsWith("rift: "));
    }

    @Test
    void aMissingRequiredOptionExitsWithTwo() throws Exception {
        Result result = run("check", "--spec", resource("/specs/swagger2-basepath.yml"));

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("--consumption"));
    }

    @Test
    void aConfidenceOutsideZeroToOneExitsWithTwo() throws Exception {
        Result result = run(check("clean-graph.json", "--min-confidence", "1.5"));

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("--min-confidence"));
    }

    @Test
    void githubFormatWritesWorkflowCommands() throws Exception {
        Result result = run(check("drift-graph.json", "--format", "github"));

        assertEquals(1, result.exitCode(), result.err());
        assertTrue(result.out().startsWith(
                "::error file=src/api.ts,line=14,title=rift MISSING_ENDPOINT::"
                        + "The backend does not declare GET /api/v2/reports"));
        assertTrue(result.out().contains("::group::Endpoints no scanned frontend code calls (2)"));
    }

    @Test
    void whenTheBuildDoesNotFailAnnotationsAreWarnings() throws Exception {
        Result result = run(check("drift-graph.json", "--format", "github", "--fail-on", "never"));

        assertEquals(0, result.exitCode(), result.err());
        assertTrue(result.out().startsWith("::warning file=src/api.ts,line=14,"));
    }

    @Test
    void theFilePrefixGoesInFrontOfEveryFilePath() throws Exception {
        Result result = run(check("drift-graph.json", "--file-prefix", ".\\web\\src\\"));

        assertTrue(result.out().contains("web/src/src/api.ts:14"));
    }

    @Test
    void aFilePrefixIsNormalized() {
        assertEquals("web/src", CheckCommand.normalizePrefix("web\\src\\"));
        assertEquals("examples/drift-demo/frontend", CheckCommand.normalizePrefix("./examples/drift-demo/frontend/"));
        assertEquals("", CheckCommand.normalizePrefix(null));
        assertEquals("", CheckCommand.normalizePrefix(""));
    }

    private static String[] check(String graph, String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "check",
                "--spec", resource("/specs/swagger2-basepath.yml"),
                "--consumption", resource("/graphs/" + graph)));
        args.addAll(List.of(extra));
        return args.toArray(new String[0]);
    }

    private static Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        CommandLine commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));

        int exitCode = commandLine.execute(args);
        return new Result(exitCode, out.toString(), err.toString());
    }

    private static String resource(String name) throws Exception {
        URL url = CheckCommandTest.class.getResource(name);
        if (url == null) {
            throw new IllegalStateException("missing test resource: " + name);
        }
        return Path.of(url.toURI()).toString();
    }
}