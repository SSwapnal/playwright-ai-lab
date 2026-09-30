package com.swapnal.migrator.runner;

/** A failed test (or a file-level load error) with its error output, ANSI colour codes removed. */
public record TestFailure(String title, String message) {
}
