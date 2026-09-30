package com.swapnal.migrator.core;

import java.util.List;

/**
 * One generate-and-run cycle.
 *
 * @param failures one line per failing test: title and the first line of its error
 */
public record AttemptRecord(int number, boolean passed, int testsRun, int testsPassed,
                            List<String> failures, long durationMs) {

    public AttemptRecord {
        failures = List.copyOf(failures);
    }
}
