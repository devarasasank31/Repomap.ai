package com.repomap.project;

/** Lifecycle of an imported repository. */
public enum ProjectStatus {

    /** Created but no source archive imported yet. */
    CREATED,

    /** A ZIP upload is being extracted right now. */
    IMPORTING,

    /** Files extracted and inventoried successfully. */
    IMPORTED,

    /** Extraction failed; {@code errorMessage} explains why. */
    IMPORT_FAILED;

    public static ProjectStatus of(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
