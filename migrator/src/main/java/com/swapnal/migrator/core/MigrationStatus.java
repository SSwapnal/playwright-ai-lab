package com.swapnal.migrator.core;

public enum MigrationStatus {
    /** Converted spec passed against the application. */
    PASSED,
    /** Still failing after the maximum number of attempts. */
    FAILED,
    /** Converted but not executed ({@code --no-run}). */
    CONVERTED_NOT_RUN,
    /** The model could not be reached or did not return usable files. */
    ERROR
}
