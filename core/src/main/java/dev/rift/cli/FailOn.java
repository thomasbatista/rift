package dev.rift.cli;

import dev.rift.model.Finding;
import dev.rift.model.Rule;

/** Which findings make {@code rift check} exit with a failure code. */
public enum FailOn {
    /** Only calls to endpoints the backend does not declare: those break the frontend. */
    MISSING,
    /** Any finding that is shown, including endpoints nobody calls. */
    ALL,
    /** Never fail; only report. */
    NEVER;

    public boolean fails(Finding finding) {
        return switch (this) {
            case MISSING -> finding.rule() == Rule.MISSING_ENDPOINT;
            case ALL -> true;
            case NEVER -> false;
        };
    }
}