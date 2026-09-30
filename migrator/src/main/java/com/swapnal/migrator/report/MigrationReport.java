package com.swapnal.migrator.report;

import com.swapnal.migrator.core.MigrationResult;
import com.swapnal.migrator.core.MigrationStatus;

import java.util.List;

/** Serialised as {@code migration-report.json}; also the model for the markdown report. */
public record MigrationReport(String generatedAt, String llm, int maxAttempts, Summary summary,
                              List<MigrationResult> results) {

    public record Summary(int testClasses, int passed, int passedFirstAttempt, int passedAfterFix,
                          int failed, int notRun, int errors) {
    }

    public static MigrationReport of(String generatedAt, String llm, int maxAttempts, List<MigrationResult> results) {
        int passed = count(results, MigrationStatus.PASSED);
        int firstTry = (int) results.stream()
                .filter(r -> r.status() == MigrationStatus.PASSED && r.attemptCount() == 1).count();
        Summary summary = new Summary(results.size(), passed, firstTry, passed - firstTry,
                count(results, MigrationStatus.FAILED), count(results, MigrationStatus.CONVERTED_NOT_RUN),
                count(results, MigrationStatus.ERROR));
        return new MigrationReport(generatedAt, llm, maxAttempts, summary, List.copyOf(results));
    }

    private static int count(List<MigrationResult> results, MigrationStatus status) {
        return (int) results.stream().filter(r -> r.status() == status).count();
    }
}
