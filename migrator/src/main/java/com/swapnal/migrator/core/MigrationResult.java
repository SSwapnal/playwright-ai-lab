package com.swapnal.migrator.core;

import com.swapnal.migrator.analysis.PatternFinding;

import java.util.List;

/** Outcome of migrating one Java test class. */
public record MigrationResult(String testClass,
                              String specFile,
                              List<String> pageFiles,
                              MigrationStatus status,
                              List<AttemptRecord> attempts,
                              List<PatternFinding> seleniumPatterns,
                              List<String> notes) {

    public MigrationResult {
        pageFiles = List.copyOf(pageFiles);
        attempts = List.copyOf(attempts);
        seleniumPatterns = List.copyOf(seleniumPatterns);
        notes = List.copyOf(notes);
    }

    public int attemptCount() {
        return attempts.size();
    }
}
