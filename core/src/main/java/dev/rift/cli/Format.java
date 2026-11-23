package dev.rift.cli;

/** Output formats of {@code rift check}. */
public enum Format {
    TEXT,
    JSON,
    /** GitHub Actions workflow commands: findings become annotations on the files. */
    GITHUB
}