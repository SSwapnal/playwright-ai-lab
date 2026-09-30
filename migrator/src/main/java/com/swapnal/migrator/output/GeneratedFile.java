package com.swapnal.migrator.output;

/** A file produced by the model, with a path relative to the Playwright project root. */
public record GeneratedFile(String path, String content) {
}
