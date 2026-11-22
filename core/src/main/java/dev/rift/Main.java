package dev.rift;

import dev.rift.cli.RiftCommand;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public final class Main {

    private Main() {
    }

    /** Built here, not in main(), so tests can run the whole CLI in-process. */
    public static CommandLine commandLine() {
        CommandLine commandLine = new CommandLine(new RiftCommand());
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.setExecutionExceptionHandler((error, cmd, parseResult) -> {
            String reason = error.getCause() == null
                    ? error.getMessage()
                    : error.getMessage() + ": " + error.getCause().getMessage();
            cmd.getErr().println("rift: " + reason);
            cmd.getErr().flush();
            return ExitCode.USAGE;
        });
        return commandLine;
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}