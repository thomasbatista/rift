package dev.rift.model;

/** The kinds of finding rift can report. */
public enum Rule {
    /** The frontend calls something the backend does not declare. */
    MISSING_ENDPOINT,
    /** The backend declares something no scanned frontend code calls. */
    ORPHAN_ENDPOINT
}