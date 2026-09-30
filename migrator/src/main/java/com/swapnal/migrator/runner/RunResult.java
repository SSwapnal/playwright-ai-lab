package com.swapnal.migrator.runner;

import java.util.List;

/** Outcome of running one spec file. */
public record RunResult(int total, int passed, List<TestFailure> failures) {

    public RunResult {
        failures = List.copyOf(failures);
    }

    public int failed() {
        return total - passed;
    }

    /** True only if tests actually ran and all of them passed. */
    public boolean success() {
        return total > 0 && passed == total && failures.isEmpty();
    }

    public static RunResult error(String title, String message) {
        return new RunResult(0, 0, List.of(new TestFailure(title, message)));
    }
}
