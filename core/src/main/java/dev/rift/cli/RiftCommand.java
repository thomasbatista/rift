package dev.rift.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "rift",
        mixinStandardHelpOptions = true,
        version = "rift 0.1.0",
        description = "Detects contract drift between a backend API spec and the frontend code that calls it.",
        subcommands = CheckCommand.class)
public class RiftCommand implements Runnable {

    @Spec
    CommandSpec commandSpec;

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }
}