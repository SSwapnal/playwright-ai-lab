package com.swapnal.migrator.runner;

/** Executes a generated spec. Abstracted so the fix loop can be unit tested without a browser. */
public interface TestRunner {

    /** @param specPath path relative to the Playwright project root, e.g. {@code tests/login.spec.ts} */
    RunResult run(String specPath);
}
